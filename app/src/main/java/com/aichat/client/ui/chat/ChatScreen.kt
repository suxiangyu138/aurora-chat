package com.aichat.client.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.remote.ImageUtils
import kotlinx.coroutines.launch

/** 聊天页:消息列表 + 流式打字机输出 + 输入栏 */
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
    val earlierAdded by viewModel.earlierAdded.collectAsStateWithLifecycle()
    val streamingText by viewModel.streamingText.collectAsStateWithLifecycle()
    val streamingReasoning by viewModel.streamingReasoning.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val pendingImage by viewModel.pendingImage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    val isStreaming = streamingText != null
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 编辑对话框状态
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

    // ---------- 智能自动滚动:仅当用户停留在底部时才跟随新消息 ----------

    // 列表头部可能有一个"加载更早"项
    val headerOffset = if (hasMore) 1 else 0
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            lastVisible >= info.totalItemsCount - 2
        }
    }
    var initialized by remember { mutableStateOf(false) }

    // 首次进入:跳到底部
    LaunchedEffect(messages.size) {
        if (!initialized && messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1 + headerOffset)
            initialized = true
        }
    }
    // 新内容到达:在底部才跟随
    LaunchedEffect(messages.size, streamingText?.length) {
        if (atBottom) {
            val total = messages.size + if (isStreaming) 1 else 0
            if (total > 0) listState.animateScrollToItem(total - 1 + headerOffset)
        }
    }
    // 加载更早的消息后:锚定到新增部分的开头,保持阅读位置
    LaunchedEffect(earlierAdded) {
        if (earlierAdded > 0) listState.scrollToItem(earlierAdded + headerOffset)
    }

    // 错误提示(密钥错误、配置缺失等瞬时提示)
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

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
            // 用户上翻查看历史时显示"回到底部"
            if (!atBottom && (messages.isNotEmpty() || isStreaming)) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            val total = messages.size + if (isStreaming) 1 else 0
                            if (total > 0) listState.animateScrollToItem(total - 1 + headerOffset)
                        }
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
                            onValueChange = { input = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("输入消息…") },
                            maxLines = 4
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
                                onClick = {
                                    viewModel.send(input)
                                    input = ""
                                },
                                enabled = input.isNotBlank() || pendingImage != null
                            ) {
                                Icon(Icons.Default.Send, contentDescription = "发送")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(vertical = 10.dp, horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 历史消息懒加载:向上滚到顶可加载更早
            if (hasMore) {
                item(key = "load_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        TextButton(onClick = { viewModel.loadEarlier() }) {
                            Text("加载更早的消息")
                        }
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageItem(
                    message = message,
                    onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                    onEdit = {
                        editText = message.content
                        editingMessage = message
                    },
                    onRegenerate = { viewModel.regenerate(message) },
                    onDelete = { viewModel.deleteMessage(message) }
                )
            }
            // 流式输出中的临时气泡(打字机效果 + 呼吸灯 + 思考过程)
            if (streamingText != null) {
                item(key = "streaming") {
                    MessageItem(
                        message = MessageEntity(
                            id = -1,
                            sessionId = sessionId,
                            role = "assistant",
                            content = streamingText.orEmpty(),
                            createdAt = 0
                        ),
                        streaming = true,
                        streamingReasoning = streamingReasoning.orEmpty()
                    )
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

/** 单条消息:头像 + 气泡 + 操作菜单 */
@Composable
private fun MessageItem(
    message: MessageEntity,
    streaming: Boolean = false,
    streamingReasoning: String = "",
    onCopy: (() -> Unit)? = null,
    onEdit: (() -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            AssistantAvatar()
            Spacer(Modifier.width(6.dp))
        }
        Box {
            Column {
                MessageBubble(
                    message = message,
                    streaming = streaming,
                    streamingReasoning = streamingReasoning,
                    onRetry = onRegenerate
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("复制") },
                    onClick = { onCopy?.invoke(); menuOpen = false }
                )
                if (isUser) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { onEdit?.invoke(); menuOpen = false }
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text("重新生成") },
                        onClick = { onRegenerate?.invoke(); menuOpen = false }
                    )
                }
                DropdownMenuItem(
                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                    onClick = { onDelete?.invoke(); menuOpen = false }
                )
            }
        }
        if (!streaming) {
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "消息操作",
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        if (isUser) {
            Spacer(Modifier.width(6.dp))
            UserAvatar()
        }
    }
}

/** 消息气泡:用户右侧主色,AI 左侧灰色;Markdown + LaTeX 渲染,流式时带光标 */
@Composable
private fun MessageBubble(
    message: MessageEntity,
    streaming: Boolean = false,
    streamingReasoning: String = "",
    onRetry: (() -> Unit)? = null
) {
    val isUser = message.role == "user"
    val darkTheme = isSystemInDarkTheme()
    // 用户气泡底色为主色,文字固定白色;AI 气泡跟随系统深浅色
    val textColorCss = if (isUser) "#FFFFFF"
    else if (darkTheme) "#E6E1E5" else "#1C1B1F"
    val contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary
    else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        color = when {
            message.isError -> MaterialTheme.colorScheme.errorContainer
            isUser -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = when {
            message.isError -> MaterialTheme.colorScheme.onErrorContainer
            isUser -> MaterialTheme.colorScheme.onPrimary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.widthIn(max = 300.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 2.dp)) {
            if (message.isError) {
                // 失败气泡:错误信息 + 内联重试按钮
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { onRetry?.invoke() }) {
                        Text(
                            "重试",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                // 思考过程:可展开/收起(流式中默认展开,历史消息默认收起)
                val reasoning = if (streaming) streamingReasoning else message.reasoning.orEmpty()
                if (reasoning.isNotBlank()) {
                    ReasoningSection(
                        reasoning = reasoning,
                        color = contentColor,
                        defaultExpanded = streaming
                    )
                }
                Row(verticalAlignment = Alignment.Top) {
                    if (streaming) {
                        BreathingDot(Modifier.padding(top = 9.dp, end = 6.dp))
                    }
                    MarkdownView(
                        content = message.content + if (streaming) " ▌" else "",
                        textColorCss = textColorCss,
                        darkTheme = darkTheme,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/** AI 头像:极光渐变圆 + 星芒 */
@Composable
private fun AssistantAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D9488), Color(0xFF6366F1), Color(0xFFC026D3))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** 用户头像:主色圆 + 人形 */
@Composable
private fun UserAvatar() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** 思考过程折叠区 */
@Composable
private fun ReasoningSection(reasoning: String, color: Color, defaultExpanded: Boolean) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "思考过程",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = color.copy(alpha = 0.65f),
                modifier = Modifier.weight(1f)
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起思考过程" else "展开思考过程",
                tint = color.copy(alpha = 0.65f),
                modifier = Modifier.size(18.dp)
            )
        }
        AnimatedVisibility(visible = expanded) {
            Text(
                reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = color.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        HorizontalDivider(color = color.copy(alpha = 0.15f))
    }
}

/** 呼吸灯:流式回答期间柔和的呼吸光点 */
@Composable
private fun BreathingDot(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "breathing")
    val alpha by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha"
    )
    val scale by transition.animateFloat(
        initialValue = 0.7f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        modifier = modifier
            .size(10.dp)
            .scale(scale)
            .alpha(alpha)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    )
}
