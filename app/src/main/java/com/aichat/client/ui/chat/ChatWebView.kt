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
    val currentOnRetry = rememberUpdatedState(onRetry)
    val currentOnLoadEarlier = rememberUpdatedState(onLoadEarlier)
    val currentOnWebViewReady = rememberUpdatedState(onWebViewReady)

    var pageLoaded by remember { mutableStateOf(false) }
    var sentMsgKey by remember { mutableStateOf("") }
    var sentThemeKey by remember { mutableStateOf("") }
    var wasStreaming by remember { mutableStateOf(false) }
    var sentStreamLen by remember { mutableStateOf(-1) }
    var sentReasonLen by remember { mutableStateOf(-1) }

    val gson = remember { Gson() }

    fun quote(s: String) = JSONObject.quote(s)

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
        val json = gson.toJson(
            currentMessages.value.map {
                mapOf(
                    "id" to it.id,
                    "role" to it.role,
                    "content" to it.content,
                    "reasoning" to it.reasoning.orEmpty(),
                    "image" to it.imageBase64.orEmpty(),
                    "status" to it.status
                )
            }
        )
        val key = json + "|" + currentHasMore.value
        if (key == sentMsgKey) return
        sentMsgKey = key
        webView.evaluateJavascript(
            "setMessages(${quote(json)}, ${currentHasMore.value})", null
        )
    }

    fun syncStreaming(webView: WebView) {
        val text = currentStreamingText.value
        if (text == null) {
            if (wasStreaming) {
                wasStreaming = false
                sentStreamLen = -1
                sentReasonLen = -1
                webView.evaluateJavascript("clearStreaming()", null)
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
