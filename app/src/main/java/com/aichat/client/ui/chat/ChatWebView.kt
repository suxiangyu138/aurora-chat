package com.aichat.client.ui.chat

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aichat.client.BuildConfig
import com.aichat.client.data.local.MessageEntity
import com.google.gson.Gson
import org.json.JSONObject

/**
 * 单页聊天视图:整个会话渲染进一个 WebView。
 * 布局/高度/滚动/流式追加全部由页面原生管理 —— 不存在气泡高度同步环节,
 * 从架构上根除气泡显示不全问题;流式按帧节流增量追加,不做全量重渲染。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ChatWebView(
    messages: List<MessageEntity>,
    chatFontSize: Int,
    streamingText: String?,
    streamingReasoning: String?,
    hasMore: Boolean,
    darkTheme: Boolean,
    userBubbleColor: String,
    assistantBubbleColor: String,
    userTextColor: String,
    assistantTextColor: String,
    errorBubbleColor: String,
    errorTextColor: String,
    onScrollState: (Boolean) -> Unit,
    onMenu: (Long) -> Unit,
    onImage: (Long, Int) -> Unit,
    onDiagram: (String) -> Unit,
    onDiagramImage: (String) -> Unit,
    onRetry: (Long) -> Unit,
    onLoadEarlier: () -> Unit,
    onWebViewReady: (WebView) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val currentMessages = rememberUpdatedState(messages)
    val currentStreamingText = rememberUpdatedState(streamingText)
    val currentStreamingReasoning = rememberUpdatedState(streamingReasoning)
    val currentHasMore = rememberUpdatedState(hasMore)
    val currentDark = rememberUpdatedState(darkTheme)
    val currentUserBubble = rememberUpdatedState(userBubbleColor)
    val currentAssistantBubble = rememberUpdatedState(assistantBubbleColor)
    val currentUserText = rememberUpdatedState(userTextColor)
    val currentAssistantText = rememberUpdatedState(assistantTextColor)
    val currentErrorBubble = rememberUpdatedState(errorBubbleColor)
    val currentErrorText = rememberUpdatedState(errorTextColor)
    val currentOnScrollState = rememberUpdatedState(onScrollState)
    val currentOnMenu = rememberUpdatedState(onMenu)
    val currentOnImage = rememberUpdatedState(onImage)
    val currentOnDiagram = rememberUpdatedState(onDiagram)
    val currentOnDiagramImage = rememberUpdatedState(onDiagramImage)
    val currentOnRetry = rememberUpdatedState(onRetry)
    val currentOnLoadEarlier = rememberUpdatedState(onLoadEarlier)
    val currentOnWebViewReady = rememberUpdatedState(onWebViewReady)

    var pageLoaded by remember { mutableStateOf(false) }
    var sentMsgKey by remember { mutableStateOf("") }
    var sentIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    var sentThemeKey by remember { mutableStateOf("") }
    var wasStreaming by remember { mutableStateOf(false) }
    var sentStreamLen by remember { mutableStateOf(-1) }
    var sentFontSize by remember { mutableStateOf(-1) }
    var sentReasonLen by remember { mutableStateOf(-1) }

    val gson = remember { Gson() }

    fun quote(s: String) = JSONObject.quote(s)

    fun syncFontSize(webView: WebView) {
        val size = chatFontSize
        if (size == sentFontSize) return
        sentFontSize = size
        webView.evaluateJavascript("setFontSize($size)", null)
    }

    fun syncTheme(webView: WebView) {
        val key = listOf(
            currentDark.value, currentUserBubble.value, currentAssistantBubble.value,
            currentUserText.value, currentAssistantText.value,
            currentErrorBubble.value, currentErrorText.value
        ).joinToString("|")
        if (key == sentThemeKey) return
        sentThemeKey = key
        val js = "setTheme(${currentDark.value}, ${quote(currentUserBubble.value)}, " +
            "${quote(currentAssistantBubble.value)}, ${quote(currentUserText.value)}, " +
            "${quote(currentAssistantText.value)}, ${quote(currentErrorBubble.value)}, " +
            "${quote(currentErrorText.value)})"
        webView.evaluateJavascript(js, null)
    }

    fun syncMessages(webView: WebView) {
        val list = currentMessages.value
        // 先比对轻量指纹再序列化:流式期间本方法每帧都会跑,
        // 直接 Gson 全量序列化会把每张图片的 base64 都转一遍,是掉帧的主因之一
        val fingerprint = buildString {
            append(currentHasMore.value)
            list.forEach { m ->
                append(m.id).append(':').append(m.content.length)
                    .append(':').append(m.reasoning?.length ?: -1)
                    .append(':').append(m.status)
                    .append(':').append(m.usageJson?.length ?: -1)
                    .append(':').append(m.imagesJson?.length ?: -1)
                    .append(':').append(m.filesJson?.length ?: -1)
                    .append(':').append(m.attachmentName?.length ?: -1)
                    .append('|')
            }
        }
        if (fingerprint == sentMsgKey) return
        sentMsgKey = fingerprint
        val userPrompt = HashMap<Int, Int>()
        var lastUser = -1
        list.forEachIndexed { index, m ->
            if (m.role == "user") lastUser = index
            if (m.role == "assistant" && !m.usageJson.isNullOrBlank()) {
                val prompt = runCatching { JSONObject(m.usageJson).optInt("prompt", 0) }
                    .getOrDefault(0)
                if (lastUser >= 0 && prompt > 0) userPrompt[lastUser] = prompt
            }
        }
        fun messageMap(m: MessageEntity, promptTokens: Int): Map<String, Any> = mapOf(
            "id" to m.id,
            "role" to m.role,
            "content" to m.content,
            "reasoning" to m.reasoning.orEmpty(),
            "image" to m.imageBase64.orEmpty(),
            "imagesJson" to m.imagesJson.orEmpty(),
            "filesJson" to m.filesJson.orEmpty(),
            "status" to m.status,
            "usage" to m.usageJson.orEmpty(),
            "attachmentName" to m.attachmentName.orEmpty(),
            "promptTokens" to promptTokens
        )

        // 追加路径:只在末尾多出一条 AI 回复时走增量追加,
        // 避免回答结束时整段会话重渲染造成的"卡一下"
        val canAppend = sentIds.isNotEmpty() &&
            list.size == sentIds.size + 1 &&
            list.take(list.size - 1).map { it.id } == sentIds &&
            list.last().role == "assistant"
        val previousIds = sentIds
        sentIds = list.map { it.id }
        if (canAppend && previousIds == list.dropLast(1).map { it.id }) {
            val last = list.last()
            val payload = gson.toJson(messageMap(last, 0))
            webView.evaluateJavascript("appendMessage(${quote(payload)})", null)
            if (currentStreamingText.value == null) {
                webView.evaluateJavascript("removeStreamingRow()", null)
            }
            return
        }

        val json = gson.toJson(list.mapIndexed { index, it -> messageMap(it, userPrompt[index] ?: 0) })
        webView.evaluateJavascript(
            "setMessages(${quote(json)}, ${currentHasMore.value})", null
        )
        // 每次正式消息变化都顺手清一次流式行:非流式期间它本就不该存在,
        // 遗留的"空气泡+闪烁光标"由此兜底清除
        if (currentStreamingText.value == null) {
            webView.evaluateJavascript("removeStreamingRow()", null)
        }
    }

    fun syncStreaming(webView: WebView) {
        val text = currentStreamingText.value
        if (text == null) {
            if (wasStreaming) {
                wasStreaming = false
                sentStreamLen = -1
                sentReasonLen = -1
                // 结束态强制移除流式行:只依赖页面内部"等正式消息到达"的判断,
                // 偶尔会因为顺序问题把光标留成一条空气泡一直闪
                webView.evaluateJavascript("clearStreaming(); removeStreamingRow();", null)
                syncMessages(webView)
            }
            return
        }
        val reasoning = currentStreamingReasoning.value.orEmpty()
        // 开流首帧 / 页面重载后恢复 / 文本回退:增量无法对齐,全量重置
        if (!wasStreaming || text.length < sentStreamLen || reasoning.length < sentReasonLen) {
            wasStreaming = true
            sentStreamLen = text.length
            sentReasonLen = reasoning.length
            webView.evaluateJavascript(
                "resetStreaming(${quote(text)}, ${quote(reasoning)})", null
            )
            return
        }
        val delta = text.substring(sentStreamLen)
        val reasoningDelta = reasoning.substring(sentReasonLen)
        if (delta.isNotEmpty() || reasoningDelta.isNotEmpty()) {
            sentStreamLen = text.length
            sentReasonLen = reasoning.length
            webView.evaluateJavascript(
                "updateStreaming(${quote(delta)}, ${quote(reasoningDelta)})", null
            )
        }
    }

    AndroidView(
        factory = { context ->
            // 远程调试仅 debug 包开启:release 包开着等于对本机暴露一个可注入 JS 的调试端口
            if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onScrollState(atBottom: Boolean) {
                        post { currentOnScrollState.value?.invoke(atBottom) }
                    }

                    @JavascriptInterface
                    fun onMenu(id: String) {
                        post { id.toLongOrNull()?.let { currentOnMenu.value?.invoke(it) } }
                    }

                    @JavascriptInterface
                    fun openDiagram(svg: String) {
                        post { currentOnDiagram.value?.invoke(svg) }
                    }

                    @JavascriptInterface
                    fun openDiagramImage(dataUrl: String) {
                        post { currentOnDiagramImage.value?.invoke(dataUrl) }
                    }

                    @JavascriptInterface
                    fun onImage(id: String, index: String) {
                        post {
                            id.toLongOrNull()?.let { sid ->
                                currentOnImage.value?.invoke(sid, index.toIntOrNull() ?: 0)
                            }
                        }
                    }

                    @JavascriptInterface
                    fun onRetry(id: String) {
                        post { id.toLongOrNull()?.let { currentOnRetry.value?.invoke(it) } }
                    }

                    @JavascriptInterface
                    fun onLoadEarlier() {
                        post { currentOnLoadEarlier.value?.invoke() }
                    }

                    @JavascriptInterface
                    fun copyText(text: String) {
                        post {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE)
                                as? ClipboardManager
                            cm?.setPrimaryClip(ClipData.newPlainText("code", text))
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        }
                    }
                }, "MdBridge")
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                        Log.d("ChatWebView", "JS[${message.messageLevel()}]: ${message.message()}")
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                        Log.d("ChatWebView", "onPageFinished: $url")
                        // 页面(重)载后 JS 侧状态归零,清掉已发送标记强制全量重推,
                        // 否则去重逻辑会直接跳过 → 白屏或只剩半截流式内容
                        sentThemeKey = ""
                        sentMsgKey = ""
                        sentIds = emptyList()
                        sentFontSize = -1
                        wasStreaming = false
                        sentStreamLen = -1
                        sentReasonLen = -1
                        view?.let {
                            syncTheme(it)
                            syncMessages(it)
                            syncStreaming(it)
                        }
                    }

                    // 安全:仅允许本地渲染页;外部链接(仅 http/https/mailto)转交系统浏览器
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString()
                        if (url != null && url.startsWith("file:///android_asset/")) {
                            return false
                        }
                        if (url != null) {
                            val uri = Uri.parse(url)
                            if (uri.scheme == "https" || uri.scheme == "http" || uri.scheme == "mailto") {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                            }
                        }
                        return true
                    }
                }
                loadUrl("file:///android_asset/md/chat.html")
                currentOnWebViewReady.value(this)
            }
        },
        update = { webView ->
            // 注意:流式期间此块每帧都会跑,不要在这里打日志
            if (!pageLoaded) return@AndroidView
            syncFontSize(webView)
            syncTheme(webView)
            syncMessages(webView)
            syncStreaming(webView)
        },
        onRelease = { webView ->
            // 离开页面必须显式销毁:AndroidView 只解除引用,WebView 自身不会回收,
            // 反复进出会话会残留多个存活实例(远程调试实测同时存在 2 个)。
            webView.stopLoading()
            webView.removeJavascriptInterface("MdBridge")
            webView.destroy()
        },
        modifier = modifier
    )
}
