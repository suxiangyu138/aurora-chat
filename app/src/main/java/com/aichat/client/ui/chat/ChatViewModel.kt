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

    val messages: StateFlow<List<MessageEntity>> = chatRepository.observeMessages(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private var retryJob: Job? = null
    private var streamAttempt = 0

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

    /** 流式失败:未收到任何内容时自动重连(指数退避),已有内容则正常收尾 */
    private fun onStreamError(app: Application, config: ModelConfig, msg: String) {
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
                _error.value = if (streamAttempt >= MAX_RETRIES && nothingReceived) {
                    "多次重连失败:$msg"
                } else msg
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
                _error.value = e.message ?: "请求失败"
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
