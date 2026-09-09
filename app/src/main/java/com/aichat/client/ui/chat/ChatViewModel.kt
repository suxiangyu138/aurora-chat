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

    private suspend fun startStreaming(config: ModelConfig, text: String, image: ChatImage?) {
        _streamingText.value = ""
        _streamingReasoning.value = ""
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
            onComplete = { full, reasoning ->
                viewModelScope.launch {
                    if (full.isNotBlank() || reasoning.isNotBlank()) {
                        chatRepository.saveAssistantMessage(
                            sessionId, full, reasoning.ifBlank { null }
                        )
                    }
                    _streamingText.value = null
                    _streamingReasoning.value = null
                }
            },
            onError = { msg ->
                viewModelScope.launch {
                    _error.value = msg
                    _streamingText.value = null
                    _streamingReasoning.value = null
                }
            }
        )
    }

    private suspend fun startNonStream(config: ModelConfig, text: String, image: ChatImage?) {
        _streamingText.value = ""   // 空串表示等待中(呼吸灯)
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
    }

    fun clearMessages() {
        viewModelScope.launch { chatRepository.clearMessages(sessionId) }
    }

    fun consumeError() {
        _error.value = null
    }

    override fun onCleared() {
        // 页面销毁自动终止网络请求,杜绝后台流量浪费与崩溃
        eventSource?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(sessionId: Long): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    ?: throw IllegalStateException("创建 ChatViewModel 缺少 Application")
                ChatViewModel(app, sessionId)
            }
        }
    }
}
