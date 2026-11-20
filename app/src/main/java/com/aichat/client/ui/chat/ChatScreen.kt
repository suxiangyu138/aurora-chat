package com.aichat.client.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.remote.ChatFile
import com.aichat.client.data.remote.ImageUtils
import com.aichat.client.data.remote.provider.ProviderCatalog
import com.aichat.client.data.remote.provider.ProviderId

/** 输入最大长度(超出提示,不静默截断) */
private const val MAX_INPUT_LENGTH = 4000

/** 把 Markdown 渲染结果还原成纯文本:去掉标记符号,保留可读内容 */
private fun markdownToPlain(text: String): String {
    var t = text
    t = t.replace(Regex("```[^\n]*\n"), "").replace("```", "")
    t = t.replace(Regex("`([^`]*)`"), "$1")
    t = t.replace(Regex("!\\[([^\\]]*)]\\([^)]*\\)"), "$1")
    t = t.replace(Regex("\\[([^\\]]+)]\\(([^)]+)\\)"), "$1 ($2)")
    t = t.replace(Regex("(?m)^#{1,6}\\s*"), "")
    t = t.replace(Regex("(?m)^>\\s?"), "")
    t = t.replace(Regex("(?m)^\\s*[-*+]\\s+"), "· ")
    t = t.replace(Regex("(?m)^\\s*\\d+\\.\\s+"), "")
    t = t.replace(Regex("(?m)^\\s*[-*_]{3,}\\s*$"), "")
    t = t.replace(Regex("\\*\\*|__|~~|\\*|_"), "")
    t = t.replace(Regex("(?m)^\\|?\\s*:?-{2,}.*$"), "")
    t = t.replace("|", " ")
    t = t.replace(Regex("[ \\t]+"), " ")
    t = t.replace(Regex("\\n{3,}"), "\n\n")
    return t.trim()
}

private fun fmtTokens(n: Int): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000f)
    n >= 1_000 -> String.format("%.1fK", n / 1_000f)
    else -> n.toString()
}

/** 顶栏统计:本会话累计 token、累计花费、当前上下文窗口占用 */
private fun statsText(s: ChatViewModel.SessionStats?): String {
    if (s == null) return "累计统计中…"
    val total = "累计 ${fmtTokens(s.totalTokens)} tok"
    val money = if (s.costKnown) "¥" + String.format("%.4f", s.totalCost) else "费用未收录"
    val window = s.contextWindow?.takeIf { it > 0 }?.let { win ->
        val pct = (s.contextUsed * 100f / win).coerceIn(0f, 999f)
        "窗口 ${fmtTokens(s.contextUsed)}/${fmtTokens(win)} (${pct.toInt()}%)"
    } ?: "窗口 ${fmtTokens(s.contextUsed)}"
    return "$total · $money · $window"
}

/** 颜色转 CSS #RRGGBB(必须带 #,裸 RRGGBB 会让整条 CSS 声明失效) */
private fun hex(c: androidx.compose.ui.graphics.Color): String =
    "#" + c.toArgb().toUInt().toString(16).padStart(8, '0').takeLast(6)

/** 聊天页:单 WebView 渲染整个会话 + 输入栏 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    sessionId: Long,
    onBack: () -> Unit,
    viewModel: ChatViewModel = viewModel(factory = ChatViewModel.factory(sessionId))
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingImages by viewModel.pendingImages.collectAsStateWithLifecycle()
    val pendingFiles by viewModel.pendingFiles.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val balance by viewModel.balance.collectAsStateWithLifecycle()
    var modelMenuOpen by remember { mutableStateOf(false) }
    val provider = ProviderId.fromKey(activeConfig?.providerId)
    val caps = ProviderCatalog.capabilitiesOf(provider)
    val thinkingOn = activeConfig?.thinkingEnabled == true
    val searchOn = activeConfig?.webSearchEnabled == true
    val snackbarHostState = remember { SnackbarHostState() }
    val isStreaming = streamingText != null
    val inputOverLimit = input.length > MAX_INPUT_LENGTH
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 滚动状态由 WebView 上报(页面原生滚动)
    var atBottom by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }

    // 消息菜单(底部弹层)与编辑对话框
    var menuMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var editingMessage by remember { mutableStateOf<MessageEntity?>(null) }
    var zoomBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var filePreview by remember { mutableStateOf<ChatFile?>(null) }
    var editText by remember { mutableStateOf("") }

    // 文档选择器:可多选,最多 30 个
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> viewModel.attachFiles(uris) }

    // 系统相册多选(最多 30 张,无需存储权限)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(30)
    ) { uris ->
        viewModel.addImagesFromUris(uris)
    }

    // 错误提示(密钥错误、配置缺失、输出中断等)
    // 切换配置或进入页面时刷新余额(仅支持的厂商会返回状态)
    LaunchedEffect(activeConfig?.id, activeConfig?.modelName) {
        viewModel.refreshBalance()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (session?.ephemeral == true) "匿名会话" else (session?.title ?: "对话"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (session?.ephemeral == true) {
                            Text(
                                "退出即焚 · 不保存到任何地方",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearMessages() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清空对话")
                    }
                }
            )
            Text(
                statsText(stats),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
            }
        },
        floatingActionButton = {
            // 上翻查看历史时显示"回到底部"
            if (!atBottom && (messages.isNotEmpty() || isStreaming)) {
                SmallFloatingActionButton(
                    onClick = {
                        webViewRef?.evaluateJavascript("scrollToBottom(true)", null)
                    }
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = "回到底部")
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.windowInsetsPadding(
                    // 避让系统手势导航条(键盘弹出时自动上移)
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)
                )
            ) {
                Column {
                    // 输入区上方一行三件:切换模型 / 深度思考 / 联网搜索
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clickable { modelMenuOpen = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        activeConfig?.modelName?.ifBlank { null } ?: "未配置模型",
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(" ▾", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                            DropdownMenu(
                                expanded = modelMenuOpen,
                                onDismissRequest = { modelMenuOpen = false }
                            ) {
                                configs.forEach { cfg ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(cfg.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    cfg.modelName.ifBlank { "未填模型" },
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.switchModel(cfg.id)
                                            modelMenuOpen = false
                                        }
                                    )
                                }
                                if (configs.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("还没有配置,去设置页添加") },
                                        onClick = { modelMenuOpen = false }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = thinkingOn,
                            onClick = { viewModel.setThinkingEnabled(!thinkingOn) },
                            enabled = caps.reasoning && activeConfig != null,
                            label = { Text("深度思考") }
                        )
                        Spacer(Modifier.width(8.dp))
                        FilterChip(
                            selected = searchOn,
                            onClick = { viewModel.setWebSearchEnabled(!searchOn) },
                            enabled = caps.nativeSearch && activeConfig != null,
                            label = { Text(if (caps.nativeSearch) "联网搜索" else "联网不支持") }
                        )
                        balance?.let { b ->
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                modifier = Modifier.clickable { viewModel.refreshBalance() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        b.text,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    // 待发送文档(可多选,逐个可删/预览)
                    if (pendingFiles.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pendingFiles.forEachIndexed { index, f ->
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 10.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            f.name,
                                            style = MaterialTheme.typography.labelMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier
                                                .widthIn(max = 160.dp)
                                                .clickable { filePreview = f }
                                        )
                                        IconButton(
                                            onClick = { viewModel.removeFile(index) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "移除",
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 待发送图片(可多选,点开预览,单个可删)
                    if (pendingImages.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pendingImages.forEachIndexed { index, img ->
                                val bitmap = remember(img) { ImageUtils.base64ToBitmap(img.base64) }
                                Box {
                                    bitmap?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = "待发送图片",
                                            modifier = Modifier
                                                .size(64.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { zoomBitmap = it }
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeImage(index) },
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "移除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        IconButton(
                            onClick = {
                                filePicker.launch(
                                    arrayOf(
                                        "application/pdf",
                                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "application/msword",
                                        "text/*",
                                        "application/json"
                                    )
                                )
                            },
                            enabled = !isStreaming
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "添加文档")
                        }
                        IconButton(
                            onClick = {
                                imagePicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            enabled = !isStreaming
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = "添加图片")
                        }
                        OutlinedTextField(
                            value = input,
                            onValueChange = { viewModel.updateInput(it) },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息…") },
                            maxLines = 6,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                            supportingText = if (inputOverLimit) {
                                {
                                    Text(
                                        "超出最大长度 ${input.length}/$MAX_INPUT_LENGTH",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            } else null
                        )
                        Spacer(Modifier.width(8.dp))
                        if (isStreaming) {
                            // 流式进行中:发送键变停止键
                            FilledIconButton(
                                onClick = { viewModel.stopStreaming() }
                            ) {
                                Icon(
                                    Icons.Default.Stop,
                                    contentDescription = "停止生成",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            FilledIconButton(
                                onClick = { viewModel.send() },
                                enabled = (input.isNotBlank() || pendingImages.isNotEmpty() || pendingFiles.isNotEmpty()) && !inputOverLimit
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "发送")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        ChatWebView(
            messages = messages,
            streamingText = streamingText,
            streamingReasoning = streamingReasoning,
            hasMore = hasMore,
            darkTheme = darkTheme,
            userBubbleColor = hex(MaterialTheme.colorScheme.primary),
            assistantBubbleColor = hex(MaterialTheme.colorScheme.surfaceVariant),
            userTextColor = hex(MaterialTheme.colorScheme.onPrimary),
            assistantTextColor = if (darkTheme) "#E6E1E5" else "#1C1B1F",
            errorBubbleColor = hex(MaterialTheme.colorScheme.errorContainer),
            errorTextColor = hex(MaterialTheme.colorScheme.onErrorContainer),
            onScrollState = { atBottom = it },
            onMenu = { id -> menuMessage = messages.firstOrNull { it.id == id } },
            onImage = { id, index ->
                val msg = messages.firstOrNull { it.id == id }
                val b64 = runCatching {
                    val arr = org.json.JSONArray(msg?.imagesJson.orEmpty())
                    arr.getJSONObject(index).optString("base64")
                }.getOrNull()?.takeIf { it.isNotBlank() } ?: msg?.imageBase64
                b64?.let { zoomBitmap = ImageUtils.base64ToBitmap(it) }
            },
            onRetry = { id -> messages.firstOrNull { it.id == id }?.let(viewModel::regenerate) },
            onLoadEarlier = { viewModel.loadEarlier() },
            onWebViewReady = { webViewRef = it },
            modifier = Modifier.fillMaxSize().padding(padding)
        )
    }

    // 消息操作菜单(底部弹层)
    menuMessage?.let { msg ->
        ModalBottomSheet(onDismissRequest = { menuMessage = null }) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(markdownToPlain(msg.content)))
                        menuMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "复制纯文本(未渲染原文)",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(msg.content))
                        menuMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("复制 Markdown 源码", modifier = Modifier.fillMaxWidth())
                }
                if (msg.role == "user") {
                    TextButton(
                        onClick = {
                            editText = msg.content
                            editingMessage = msg
                            menuMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("编辑", modifier = Modifier.fillMaxWidth()) }
                } else {
                    TextButton(
                        onClick = {
                            viewModel.regenerate(msg)
                            menuMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重新生成", modifier = Modifier.fillMaxWidth()) }
                }
                TextButton(
                    onClick = {
                        viewModel.deleteMessage(msg)
                        menuMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "删除",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(24.dp))
            }
        }
    }

    // 文档内容预览
    filePreview?.let { f ->
        AlertDialog(
            onDismissRequest = { filePreview = null },
            title = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(f.text.take(8000), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(onClick = { filePreview = null }) { Text("关闭") }
            }
        )
    }

    // 图片放大查看:双指缩放、拖动,点右上角关闭
    zoomBitmap?.let { bmp ->
        Dialog(
            onDismissRequest = { zoomBitmap = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset += pan
                        }
                    }
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "查看图片",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                )
                IconButton(
                    onClick = { zoomBitmap = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "关闭", tint = Color.White)
                }
            }
        }
    }

    // 编辑消息对话框
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("编辑消息") },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 2,
                    maxLines = 6
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.editAndResend(msg, editText)
                    editingMessage = null
                }) { Text("重新发送") }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) { Text("取消") }
            }
        )
    }
}
