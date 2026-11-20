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
import com.aichat.client.data.remote.ChatFile
import com.aichat.client.data.remote.ChatImage
import com.aichat.client.data.remote.FileExtractor
import com.aichat.client.data.remote.ImageUtils
import com.aichat.client.data.remote.TokenUsage
import com.aichat.client.data.remote.provider.ProviderCatalog
import com.aichat.client.data.remote.provider.ProviderId
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.Pricing
import com.aichat.client.service.ChatKeepAliveService
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
    private val settingsRepository = (application as ChatApplication).settingsRepository
    private val providerApiClient = (application as ChatApplication).providerApiClient

    /** 已保存的全部模型配置(输入区切换用) */
    val configs: StateFlow<List<ModelConfig>> = settingsRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** 当前生效配置 */
    val activeConfig: StateFlow<ModelConfig?> = settingsRepository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 切换模型配置:只影响后续发送的请求,已入库的历史消息不改写 */
    fun switchModel(configId: String) {
        viewModelScope.launch { settingsRepository.setActive(configId) }
    }

    /** 余额胶囊:仅对提供 Key 级余额接口的厂商显示 */
    data class BalanceUi(val text: String, val loading: Boolean = false)
    private val _balance = MutableStateFlow<BalanceUi?>(null)
    val balance: StateFlow<BalanceUi?> = _balance.asStateFlow()

    fun refreshBalance() {
        val config = activeConfig.value ?: run {
            _balance.value = null
            return
        }
        if (!providerApiClient.supportsBalance(ProviderId.fromKey(config.providerId))) {
            _balance.value = null
            return
        }
        _balance.value = BalanceUi("余额查询中", loading = true)
        viewModelScope.launch {
            providerApiClient.fetchBalance(config).fold(
                onSuccess = { b ->
                    val symbol = if (b.currency == "USD") "$" else "¥"
                    val text = "余额 " + symbol + String.format("%.2f", b.amount) +
                        if (b.note.isNotBlank()) " · ${b.note}" else ""
                    _balance.value = BalanceUi(text)
                },
                onFailure = { e ->
                    _balance.value = BalanceUi("余额:${e.message ?: "查询失败"}")
                }
            )
        }
    }

    /** 整段会话的累计值与当前上下文占用(顶栏展示) */
    data class SessionStats(
        val totalTokens: Int,
        val totalCost: Double,
        val costKnown: Boolean,
        val contextUsed: Int,
        val contextWindow: Int?
    )

    /** 消息表变化时重算累计用量(只读 usageJson,不加载消息正文) */
    val stats: StateFlow<SessionStats?> by lazy {
        messages
            .map { loadStats() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    }

    private suspend fun loadStats(): SessionStats {
        val entries = chatRepository.sessionUsage(sessionId)
        var prompt = 0
        var completion = 0
        var cost = 0.0
        var costKnown = false
        var lastUsed = 0
        for (raw in entries) {
            val o = runCatching { org.json.JSONObject(raw) }.getOrNull() ?: continue
            val p = o.optInt("prompt")
            val c = o.optInt("completion")
            prompt += p
            completion += c
            lastUsed = p + c
            if (o.has("cost")) {
                cost += o.optDouble("cost", 0.0)
                costKnown = true
            }
        }
        val config = activeConfig.value
        val window = config?.let {
            ProviderCatalog.findModel(ProviderId.fromKey(it.providerId), it.modelName)?.contextWindow
        }
        return SessionStats(
            totalTokens = prompt + completion,
            totalCost = cost,
            costKnown = costKnown,
            contextUsed = lastUsed,
            contextWindow = window
        )
    }

    /** 活跃配置快照:入库时按它计算费用 */
    private var roundConfig: ModelConfig? = null

    private fun buildUsageJson(usage: TokenUsage, config: ModelConfig?): String {
        val cost = config?.let {
            Pricing.costCny(
                it.modelName, usage.promptTokens, usage.cachedTokens, usage.completionTokens
            )
        }
        return JsonObject().apply {
            addProperty("prompt", usage.promptTokens)
            addProperty("completion", usage.completionTokens)
            addProperty("cached", usage.cachedTokens)
            addProperty("reasoning", usage.reasoningTokens)
            addProperty("total", usage.totalTokens)
            addProperty("model", config?.modelName.orEmpty())
            if (cost != null) addProperty("cost", cost)
        }.toString()
    }

    private var pendingUsage: TokenUsage? = null

    private fun beginRound(config: ModelConfig? = null) {
        pendingUsage = null
        roundConfig = config ?: activeConfig.value
    }

    private fun finishRound() {
        refreshBalance()
    }

    /** 深度思考开关:默认关闭,按配置持久化,只影响后续请求 */
    fun setThinkingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val c = chatRepository.getActiveConfig() ?: return@launch
            settingsRepository.saveConfig(c.copy(thinkingEnabled = enabled))
        }
    }

    /** 联网搜索开关:默认开启 */
    fun setWebSearchEnabled(enabled: Boolean) {
        viewModelScope.launch {
            val c = chatRepository.getActiveConfig() ?: return@launch
            settingsRepository.saveConfig(c.copy(webSearchEnabled = enabled))
        }
    }

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
        refreshBalance()
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

    /** 待发送的图片(多选,最多 30 张) */
    private val _pendingImages = MutableStateFlow<List<ChatImage>>(emptyList())
    val pendingImages: StateFlow<List<ChatImage>> = _pendingImages.asStateFlow()

    /** 待发送的文档(多选,最多 30 个) */
    private val _pendingFiles = MutableStateFlow<List<ChatFile>>(emptyList())
    val pendingFiles: StateFlow<List<ChatFile>> = _pendingFiles.asStateFlow()

    /** 多选图片:解码压缩放 IO 线程,避免一次选 30 张时卡 UI */
    fun addImagesFromUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val images = withContext(Dispatchers.IO) {
                uris.take(MAX_ATTACHMENTS).mapNotNull {
                    ImageUtils.uriToChatImage(getApplication(), it)
                }
            }
            addImages(images)
        }
    }

    fun addImages(images: List<ChatImage>) {
        if (images.isEmpty()) return
        _pendingImages.value = (_pendingImages.value + images).take(MAX_ATTACHMENTS)
    }

    fun removeImage(index: Int) {
        _pendingImages.value = _pendingImages.value.filterIndexed { i, _ -> i != index }
    }

    /** 逐个解析文档;单个失败不打断其余 */
    fun attachFiles(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val added = mutableListOf<ChatFile>()
            var firstError: String? = null
            withContext(Dispatchers.IO) {
                for (uri in uris.take(MAX_ATTACHMENTS)) {
                    FileExtractor.extract(getApplication(), uri).fold(
                        onSuccess = { added += it },
                        onFailure = { if (firstError == null) firstError = it.message }
                    )
                }
            }
            if (added.isNotEmpty()) {
                _pendingFiles.value = (_pendingFiles.value + added).take(MAX_ATTACHMENTS)
            }
            firstError?.let { _error.value = it }
        }
    }

    fun removeFile(index: Int) {
        _pendingFiles.value = _pendingFiles.value.filterIndexed { i, _ -> i != index }
    }

    private var eventSource: EventSource? = null
    private var retryJob: Job? = null
    private var streamAttempt = 0
    private var userStopped = false

    // ---------- 输入框状态(草稿保留:切页不丢失) ----------

    private val app = getApplication<ChatApplication>()

    private val _input = MutableStateFlow(app.chatDrafts[sessionId].orEmpty())
    val input: StateFlow<String> = _input.asStateFlow()

    fun updateInput(text: String) {
        _input.value = text
        app.chatDrafts[sessionId] = text
    }

    fun send() {
        val typed = _input.value.trim()
        val text = typed.ifEmpty {
            when {
                _pendingFiles.value.isNotEmpty() -> "请阅读附件并总结要点。"
                _pendingImages.value.isNotEmpty() -> "请描述这些图片。"
                else -> ""
            }
        }
        if (text.isEmpty()) return
        if (_streamingText.value != null) return
        _input.value = ""
        app.chatDrafts.remove(sessionId)
        viewModelScope.launch {
            val config = chatRepository.getActiveConfig()
            if (config == null || !config.isComplete) {
                _error.value = "请先在设置页填写接口 URL、API Key 和模型名称"
                return@launch
            }
            val images = _pendingImages.value
            val files = _pendingFiles.value
            _pendingImages.value = emptyList()
            _pendingFiles.value = emptyList()
            beginRound(config)
            if (config.streamEnabled) {
                startStreaming(config, text, images, files)
            } else {
                startNonStream(config, text, images, files)
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
                val usageJson = pendingUsage?.let { buildUsageJson(it, roundConfig) }
                chatRepository.saveAssistantMessage(
                    sessionId, partial, partialReasoning.ifBlank { null }, usageJson
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
                startStreaming(config, text, emptyList(), emptyList())
            } else {
                startNonStream(config, text, emptyList(), emptyList())
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
            beginRound(config)
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
                onUsage = { usage -> pendingUsage = usage },
                onComplete = { full, reasoning -> onStreamDone(app, full, reasoning) },
                onInterrupted = { full, reasoning, msg ->
                    onStreamInterrupted(app, full, reasoning, msg)
                },
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

    private suspend fun startStreaming(
        config: ModelConfig,
        text: String,
        images: List<ChatImage>,
        files: List<ChatFile>
    ) {
        val app = getApplication<Application>()
        _streamingText.value = ""
        _streamingReasoning.value = ""
        // 保活:流式期间前台服务运行,防止 OriginOS 杀进程
        ChatKeepAliveService.start(app)
        eventSource = chatRepository.sendMessageStream(
            sessionId = sessionId,
            config = config,
            question = text,
            images = images,
            files = files,
            onDelta = { delta ->
                _streamingText.value = (_streamingText.value ?: "") + delta
            },
            onReasoning = { piece ->
                _streamingReasoning.value = (_streamingReasoning.value ?: "") + piece
            },
            onUsage = { usage -> pendingUsage = usage },
            onComplete = { full, reasoning -> onStreamDone(app, full, reasoning) },
            onInterrupted = { full, reasoning, msg ->
                onStreamInterrupted(app, full, reasoning, msg)
            },
            onError = { msg -> onStreamError(app, config, msg) }
        )
    }

    private fun onStreamDone(app: Application, full: String, reasoning: String) {
        finishRound()
        // 先收掉流式行再入库:反过来的话正式气泡与流式行会短暂并存,出现"两个回答"
        _streamingText.value = null
        _streamingReasoning.value = null
        viewModelScope.launch {
            streamAttempt = 0
            if (full.isNotBlank() || reasoning.isNotBlank()) {
                val usageJson = pendingUsage?.let { buildUsageJson(it, roundConfig) }
                chatRepository.saveAssistantMessage(
                    sessionId, full, reasoning.ifBlank { null }, usageJson
                )
            }
            ChatKeepAliveService.stop(app)
        }
    }

    /** 流式中断(已有部分内容):保存已生成内容,明确提示,可重新生成继续 */
    private fun onStreamInterrupted(app: Application, full: String, reasoning: String, msg: String) {
        viewModelScope.launch {
            streamAttempt = 0
            if (full.isNotBlank() || reasoning.isNotBlank()) {
                val usageJson = pendingUsage?.let { buildUsageJson(it, roundConfig) }
                chatRepository.saveAssistantMessage(
                    sessionId, full, reasoning.ifBlank { null }, usageJson
                )
            }
            _streamingText.value = null
            _streamingReasoning.value = null
            ChatKeepAliveService.stop(app)
            _error.value = "输出中断($msg),已保存当前内容,可重新生成继续"
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
            onUsage = { usage -> pendingUsage = usage },
            onComplete = { full, reasoning ->
                onStreamDone(getApplication(), full, reasoning)
            },
            onInterrupted = { full, reasoning, msg ->
                onStreamInterrupted(getApplication(), full, reasoning, msg)
            },
            onError = { msg ->
                onStreamError(getApplication(), config, msg)
            }
        )
    }

    private suspend fun startNonStream(
        config: ModelConfig,
        text: String,
        images: List<ChatImage>,
        files: List<ChatFile>
    ) {
        val app = getApplication<Application>()
        _streamingText.value = ""   // 空串表示等待中(呼吸灯)
        ChatKeepAliveService.start(app)
        beginRound()
        val result = chatRepository.sendMessageOnce(sessionId, config, text, images, files)
        result.fold(
            onSuccess = { chatResult ->
                chatResult.usage?.let { pendingUsage = it }
                finishRound()
                if (chatResult.content.isNotBlank() || !chatResult.reasoning.isNullOrBlank()) {
                    val usageJson = chatResult.usage?.let { buildUsageJson(it, roundConfig) }
                    chatRepository.saveAssistantMessage(
                        sessionId, chatResult.content, chatResult.reasoning, usageJson
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
        // 匿名会话:离开页面立刻整段删除,绝不保留
        if (session.value?.ephemeral == true) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { sessionRepository.deleteSession(sessionId) }
            }
        }
        super.onCleared()
    }

    companion object {
        private const val PAGE_SIZE = 30
        private const val MAX_ATTACHMENTS = 30
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
