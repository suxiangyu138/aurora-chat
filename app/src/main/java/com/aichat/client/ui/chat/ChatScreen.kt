package com.aichat.client.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.remote.ImageUtils

/** 输入最大长度(超出提示,不静默截断) */
private const val MAX_INPUT_LENGTH = 4000

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
    val pendingImage by viewModel.pendingImage.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
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
    var editText by remember { mutableStateOf("") }

    // 系统相册选择器(无需存储权限)
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { u ->
            ImageUtils.uriToChatImage(context, u)?.let { viewModel.attachImage(it) }
        }
    }

    // 错误提示(密钥错误、配置缺失、输出中断等)
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
            TopAppBar(
                title = {
                    Text(
                        session?.title ?: "对话",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
                    // 待发送图片预览
                    val pending = pendingImage
                    if (pending != null) {
                        val bitmap = remember(pending) {
                            ImageUtils.base64ToBitmap(pending.base64)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bitmap?.let {
                                Image(
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = "待发送图片",
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { viewModel.removeImage() }) {
                                Icon(Icons.Default.Close, contentDescription = "移除图片")
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
                                enabled = (input.isNotBlank() || pendingImage != null) && !inputOverLimit
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
                        clipboard.setText(AnnotatedString(msg.content))
                        menuMessage = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("复制", modifier = Modifier.fillMaxWidth()) }
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
