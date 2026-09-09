package com.aichat.client.ui.chat

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Markdown + LaTeX 渲染视图(WebView 方案):
 * 加载 assets/md 中的 markdown-it + KaTeX + highlight.js,完整支持
 * 标题/列表/表格/引用/代码高亮/行内与块级公式,内容高度通过 JS 桥回传自适应。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MarkdownView(
    content: String,
    textColorCss: String,
    darkTheme: Boolean,
    modifier: Modifier = Modifier
) {
    val currentContent = rememberUpdatedState(content)
    val currentColor = rememberUpdatedState(textColorCss)
    val currentDark = rememberUpdatedState(darkTheme)
    var heightDp by remember { mutableFloatStateOf(28f) }
    var pageLoaded by remember { mutableStateOf(false) }
    var sentKey by remember { mutableStateOf("") }
    val density = LocalDensity.current

    fun sendContent(webView: WebView) {
        val key = "${currentContent.value}|${currentColor.value}|${currentDark.value}"
        if (key == sentKey) return
        sentKey = key
        val js = "setContent(${JSONObject.quote(currentContent.value)}, " +
            "${JSONObject.quote(currentColor.value)}, ${currentDark.value})"
        webView.evaluateJavascript(js, null)
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                // JS 桥:渲染完成后回传内容高度(px)
                addJavascriptInterface(object {
                    @JavascriptInterface
                    fun onHeight(px: Float) {
                        post {
                            // 加冗余量:部分机型/字体下 WebView 上报高度略小于实际渲染高度,
                            // 最后一行会被裁切;多留几 dp 保证单行文本完整显示
                            heightDp = (px / resources.displayMetrics.density).coerceAtLeast(24f) + 4f
                        }
                    }
                }, "MdBridge")
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        pageLoaded = true
                        view?.let { sendContent(it) }
                    }
                }
                loadUrl("file:///android_asset/md/template.html")
            }
        },
        update = { webView ->
            if (pageLoaded) sendContent(webView)
        },
        modifier = modifier.height(with(density) { heightDp.dp })
    )
}
