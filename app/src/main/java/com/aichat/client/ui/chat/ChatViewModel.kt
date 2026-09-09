package com.aichat.client.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aichat.client.ChatApplication
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.local.SessionEntity
import com.aichat.client.data.remote.ChatImage
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.service.ChatKeepAliveService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import okhttp3.sse.EventSource

class ChatViewModel(
    application: Application,
    private val sessionId: Long
) : AndroidViewModel(application) {

    private val chatRepository = (application as ChatApplication).chatRepository
    private val sessionRepository = (application as ChatApplication).sessionRepository

    val session: StateFlow<SessionEntity?> = sessionRepository.observeSession(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ---------- 消息列表(分页懒加载:默认最新 PAGE_SIZE 条,向上滚动可加载更早) ----------

    private val _limit = MutableStateFlow(PAGE_SIZE)

    val messages: StateFlow<List<MessageEntity>> = _limit
        .flatMapLatest { limit -> chatRepository.observeLatestMessages(sessionId, limit) }
        .map { it.reversed() }   // 倒序查询 → 正序展示
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    /** 本次"加载更早"新增的条数(滚动锚点用) */
    private val _earlierAdded = MutableStateFlow(0)
    val earlierAdded: StateFlow<Int> = _earlierAdded.asStateFlow()

    init {
        viewModelScope.launch {
            val total = chatRepository.messageCount(sessionId)
            _hasMore.value = total > PAGE_SIZE
        }
    }

    fun loadEarlier() {
        viewModelScope.launch {
            val before = messages.value.size
            val total = chatRepository.messageCount(sessionId)
            _limit.value += PAGE_SIZE
            _hasMore.value = total > _limit.value
            _earlierAdded.value = (total - before).coerceIn(0, PAGE_SIZE)
        }
    }

    // ---------- 流式状态 ----------

    /** 流式接收中的增量文本;null 表示未在流式输出 */
    private val _streamingText = MutableStateFlow<String?>(null)
    val streamingText: StateFlow<String?> = _streamingText.asStateFlow()

    /** 流式接收中的思考过程增量;null 表示未在流式输出 */
    private val _streamingReasoning = MutableStateFlow<String?>(null)
    val streamingReasoning: StateFlow<String?> = _streamingReasoning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** 待发送的图片(多模态输入) */
    private val _pendingImage = MutableStateFlow<ChatImage?>(null)
    val pendingImage: StateFlow<ChatImage?> = _pendingImage.asStateFlow()

    private var eventSource: EventSource? = null
    private var retryJob: Job? = null
    private var streamAttempt = 0
    private var userStopped = false

    fun attachImage(image: ChatImage) {
        _pendingImage.value = image
    }

    fun removeImage() {
        _pendingImage.value = null
    }

    fun send(question: String) {
        val text = question.trim()
        if (text.isEmpty() && _pendingImage.value == null) return
        if (_streamingText.value != null) return
        viewModelScope.launch {
            val config = chatRepository.getActiveConfig()
            if (config == null || !config.isComplete) {
                _error.value = "请先在设置页填写接口 URL、API Key 和模型名称"
                return@launch
            }
            val image = _pendingImage.value
            _pendingImage.value = null
            if (config.streamEnabled) {
                startStreaming(config, text, image)
            } else {
                startNonStream(config, text, image)
            }
        }
    }

    /** 停止生成:终止 SSE,保留已收到的半截回复入库 */
    fun stopStreaming() {
        if (_streamingText.value == null) return
        userStopped = true
        retryJob?.cancel()
        eventSource?.cancel()
        viewModelScope.launch {
            val partial = _streamingText.value.orEmpty()
            val partialReasoning = _streamingReasoning.value.orEmpty()
            if (partial.isNotBlank() || partialReasoning.isNotBlank()) {
                chatRepository.saveAssistantMessage(
                    sessionId, partial, partialReasoning.ifBlank { null }
                )
            }
            _streamingText.value = null
            _streamingReasoning.value = null
            ChatKeepAliveService.stop(getApplication())
            userStopped = false
        }
    }

    // ---------- 消息操作 ----------

    fun deleteMessage(message: MessageEntity) {
        viewModelScope.launch { chatRepository.deleteMessage(message.id) }
    }

    /** 编辑用户消息:更新内容、删除其后所有消息并重新发送 */
    fun editAndResend(message: MessageEntity, newText: String) {
        val text = newText.trim()
        if (text.isEmpty() || _streamingText.value != null) return
        viewModelScope.launch {
            val config = chatRepository.getActiveConfig()
            if (config == null || !config.isComplete) {
                _error.value = "请先在设置页填写接口 URL、API Key 和模型名称"
                return@launch
            }
            chatRepository.editUserMessage(sessionId, message.id, text)
            if (config.streamEnabled) {
                startStreaming(config, text, null)
            } else {
                startNonStream(config, text, null)
            }
        }
    }

    /** 重新生成(或重试失败消息):删除该条 AI 消息,重发它前面的用户问题 */
    fun regenerate(message: MessageEntity) {
        if (_streamingText.value != null) return
        viewModelScope.launch {
            val config = chatRepository.getActiveConfig()
            if (config == null || !config.isComplete) {
                _error.value = "请先在设置页填写接口 URL、API Key 和模型名称"
                return@launch
            }
            val app = getApplication<Application>()
            _streamingText.value = ""
            _streamingReasoning.value = ""
            ChatKeepAliveService.start(app)
            val source = chatRepository.regenerate(
                sessionId = sessionId,
                assistantMessageId = message.id,
                config = config,
                onDelta = { delta ->
                    _streamingText.value = (_streamingText.value ?: "") + delta
                },
                onReasoning = { piece ->
                    _streamingReasoning.value = (_streamingReasoning.value ?: "") + piece
                },
                onComplete = { full, reasoning -> onStreamDone(app, full, reasoning) },
                onError = { msg -> onStreamError(app, config, msg) }
            )
            if (source == null) {
                _streamingText.value = null
                _streamingReasoning.value = null
                ChatKeepAliveService.stop(app)
            } else {
                eventSource = source
            }
        }
    }

    // ---------- 流式/非流式执行 ----------

    private suspend fun startStreaming(config: ModelConfig, text: String, image: ChatImage?) {
        val app = getApplication<Application>()
        _streamingText.value = ""
        _streamingReasoning.value = ""
        // 保活:流式期间前台服务运行,防止 OriginOS 杀进程
        ChatKeepAliveService.start(app)
        eventSource = chatRepository.sendMessageStream(
            sessionId = sessionId,
            config = config,
            question = text,
            image = image,
            onDelta = { delta ->
                _streamingText.value = (_streamingText.value ?: "") + delta
            },
            onReasoning = { piece ->
                _streamingReasoning.value = (_streamingReasoning.value ?: "") + piece
            },
            onComplete = { full, reasoning -> onStreamDone(app, full, reasoning) },
            onError = { msg -> onStreamError(app, config, msg) }
        )
    }

    private fun onStreamDone(app: Application, full: String, reasoning: String) {
        viewModelScope.launch {
            streamAttempt = 0
            if (full.isNotBlank() || reasoning.isNotBlank()) {
                chatRepository.saveAssistantMessage(sessionId, full, reasoning.ifBlank { null })
            }
            _streamingText.value = null
            _streamingReasoning.value = null
            ChatKeepAliveService.stop(app)
        }
    }

    /** 流式失败:未收到任何内容时自动重连(指数退避);重试耗尽则落库错误气泡供手动重试 */
    private fun onStreamError(app: Application, config: ModelConfig, msg: String) {
        if (userStopped) return   // 用户主动停止,stopStreaming 已收尾
        val nothingReceived = _streamingText.value.isNullOrEmpty() &&
            _streamingReasoning.value.isNullOrEmpty()
        if (nothingReceived && streamAttempt < MAX_RETRIES) {
            streamAttempt++
            _error.value = "连接中断,自动重连中(${streamAttempt}/$MAX_RETRIES)…"
            retryJob = viewModelScope.launch {
                delay(1500L * streamAttempt)   // 1.5s / 3s / 4.5s 退避
                retryStreaming(config)
            }
        } else {
            viewModelScope.launch {
                val finalMsg = if (streamAttempt >= MAX_RETRIES && nothingReceived) {
                    "多次重连失败:$msg"
                } else msg
                // 失败消息持久化,气泡内提供重试按钮
                chatRepository.saveErrorMessage(sessionId, "回答失败:$finalMsg")
                _streamingText.value = null
                _streamingReasoning.value = null
                ChatKeepAliveService.stop(app)
            }
        }
    }

    private suspend fun retryStreaming(config: ModelConfig) {
        eventSource = chatRepository.retryStream(
            sessionId = sessionId,
            config = config,
            onDelta = { delta ->
                _streamingText.value = (_streamingText.value ?: "") + delta
            },
            onReasoning = { piece ->
                _streamingReasoning.value = (_streamingReasoning.value ?: "") + piece
            },
            onComplete = { full, reasoning ->
                onStreamDone(getApplication(), full, reasoning)
            },
            onError = { msg ->
                onStreamError(getApplication(), config, msg)
            }
        )
    }

    private suspend fun startNonStream(config: ModelConfig, text: String, image: ChatImage?) {
        val app = getApplication<Application>()
        _streamingText.value = ""   // 空串表示等待中(呼吸灯)
        ChatKeepAliveService.start(app)
        val result = chatRepository.sendMessageOnce(sessionId, config, text, image)
        result.fold(
            onSuccess = { chatResult ->
                if (chatResult.content.isNotBlank() || !chatResult.reasoning.isNullOrBlank()) {
                    chatRepository.saveAssistantMessage(
                        sessionId, chatResult.content, chatResult.reasoning
                    )
                }
            },
            onFailure = { e ->
                chatRepository.saveErrorMessage(sessionId, "回答失败:${e.message ?: "请求失败"}")
            }
        )
        _streamingText.value = null
        ChatKeepAliveService.stop(app)
    }

    fun clearMessages() {
        viewModelScope.launch { chatRepository.clearMessages(sessionId) }
    }

    fun consumeError() {
        _error.value = null
    }

    override fun onCleared() {
        // 页面销毁自动终止网络请求与保活服务,杜绝后台流量浪费与崩溃
        retryJob?.cancel()
        eventSource?.cancel()
        ChatKeepAliveService.stop(getApplication())
        super.onCleared()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MAX_RETRIES = 3

        fun factory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("创建 ChatViewModel 缺少 Application")
                ChatViewModel(app, sessionId)
            }
        }
    }
}
