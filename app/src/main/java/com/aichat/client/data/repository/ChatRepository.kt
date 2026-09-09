package com.aichat.client.data.repository

import com.aichat.client.data.local.AppDatabase
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.remote.ChatApiClient
import com.aichat.client.data.remote.ChatImage
import com.aichat.client.data.remote.ChatMessage
import com.aichat.client.data.remote.ChatResult
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import okhttp3.sse.EventSource

/** 聊天核心仓库:本地存储 + 通用 API 调用的编排 */
class ChatRepository(
    private val database: AppDatabase,
    private val apiClient: ChatApiClient,
    private val settingsRepository: SettingsRepository
) {

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
        database.messageDao().observeBySession(sessionId)

    /** 分页观察:最新 N 条(倒序返回,调用方反转显示),历史懒加载 */
    fun observeLatestMessages(sessionId: Long, limit: Int): Flow<List<MessageEntity>> =
        database.messageDao().observeLatest(sessionId, limit)

    suspend fun messageCount(sessionId: Long): Int = database.messageDao().count(sessionId)

    suspend fun getActiveConfig(): ModelConfig? = settingsRepository.getActiveConfig()

    /** 组装上下文:最近 N 轮历史 + 新问题(新问题可携带图片) */
    private suspend fun buildContext(
        sessionId: Long,
        newQuestion: String,
        rounds: Int,
        image: ChatImage? = null
    ): List<ChatMessage> {
        val history = database.messageDao().getRecent(sessionId, rounds * 2)
        return history.reversed().map { ChatMessage(it.role, it.content) } +
            ChatMessage("user", newQuestion, image?.base64, image?.mime)
    }

    /**
     * 发送消息并流式接收回复。
     * 用户消息立即入库;AI 回复通过 onDelta 增量回调,onComplete 后由调用方保存入库。
     * 返回 EventSource 供调用方在页面销毁时 cancel()。
     */
    suspend fun sendMessageStream(
        sessionId: Long,
        config: ModelConfig,
        question: String,
        image: ChatImage? = null,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val now = System.currentTimeMillis()
        val isFirstMessage = database.messageDao().getFirst(sessionId) == null

        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = question,
                imageBase64 = image?.base64,
                createdAt = now
            )
        )
        database.sessionDao().touch(sessionId, now)

        // 首条消息自动生成会话标题
        if (isFirstMessage) {
            val title = question.replace("\n", " ").take(20).ifBlank { "新会话" }
            database.sessionDao().rename(sessionId, title)
        }

        val messages = buildContext(sessionId, question, config.contextRounds, image)
        return apiClient.streamChat(config, messages, onDelta, onReasoning, onComplete, onError)
    }

    /** 断线重连:不重复入库,直接用库中历史(含最后一条用户消息)重发请求 */
    suspend fun retryStream(
        sessionId: Long,
        config: ModelConfig,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val history = database.messageDao().getRecent(sessionId, config.contextRounds * 2 + 1)
        val messages = history.reversed().map { ChatMessage(it.role, it.content) }
        return apiClient.streamChat(config, messages, onDelta, onReasoning, onComplete, onError)
    }

    /** AI 回复完整入库(含思考过程) */
    suspend fun saveAssistantMessage(sessionId: Long, content: String, reasoning: String? = null) {
        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "assistant",
                content = content,
                reasoning = reasoning,
                createdAt = System.currentTimeMillis()
            )
        )
        database.sessionDao().touch(sessionId, System.currentTimeMillis())
    }

    /** 失败消息入库(展示重试按钮) */
    suspend fun saveErrorMessage(sessionId: Long, content: String) {
        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "assistant",
                content = content,
                status = "error",
                createdAt = System.currentTimeMillis()
            )
        )
        database.sessionDao().touch(sessionId, System.currentTimeMillis())
    }

    /** 删除单条消息 */
    suspend fun deleteMessage(id: Long) = database.messageDao().deleteById(id)

    /** 编辑用户消息:更新内容并删除其后所有消息,返回新文本供重发 */
    suspend fun editUserMessage(sessionId: Long, messageId: Long, newContent: String): String {
        database.messageDao().updateContent(messageId, newContent)
        database.messageDao().deleteFrom(sessionId, messageId)
        return newContent
    }

    /**
     * 重新生成/重试:删除该条 AI 消息,取它之前最近一条用户消息作为问题,
     * 不重复插入用户消息,直接用库中上下文发起流式请求。
     */
    suspend fun regenerate(
        sessionId: Long,
        assistantMessageId: Long,
        config: ModelConfig,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource? {
        val userMessage = database.messageDao().getLastUserBefore(sessionId, assistantMessageId)
            ?: return null
        database.messageDao().deleteById(assistantMessageId)
        val history = database.messageDao().getRecent(sessionId, config.contextRounds * 2 + 1)
        val messages = history.reversed().map { ChatMessage(it.role, it.content) }
        return apiClient.streamChat(config, messages, onDelta, onReasoning, onComplete, onError)
    }

    /** 非流式单次问答(用户消息已入库,调用方保存返回结果) */
    suspend fun sendMessageOnce(
        sessionId: Long,
        config: ModelConfig,
        question: String,
        image: ChatImage? = null
    ): Result<ChatResult> {
        val now = System.currentTimeMillis()
        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = question,
                imageBase64 = image?.base64,
                createdAt = now
            )
        )
        database.sessionDao().touch(sessionId, now)
        val messages = buildContext(sessionId, question, config.contextRounds, image)
        return apiClient.chatOnce(config, messages)
    }

    suspend fun clearMessages(sessionId: Long) = database.messageDao().deleteBySession(sessionId)
}
