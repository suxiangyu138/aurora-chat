package com.aichat.client.data.repository

import com.aichat.client.data.local.AppDatabase
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.remote.ChatApiClient
import com.aichat.client.data.remote.ChatFile
import com.aichat.client.data.remote.ChatImage
import com.aichat.client.data.remote.ChatMessage
import com.aichat.client.data.remote.ChatResult
import com.aichat.client.data.remote.TokenUsage
import org.json.JSONArray
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.SettingsRepository
import kotlinx.coroutines.flow.Flow
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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

    /** 系统提示:当前时间 + 可选的个人画像(设置页开关控制) */
    private suspend fun systemMessage(): ChatMessage {
        val now = ZonedDateTime.now()
        val text = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm EEEE", Locale.CHINA))
        var prompt = "当前时间:$text(时区 ${now.zone.id})。回答日期、时间、时效性问题时以此为准;" +
            "需要最新资讯或网页内容时使用联网搜索。"
        val (enabled, profile) = settingsRepository.getProfile()
        if (enabled && profile.isNotBlank()) {
            prompt += "\n\n用户画像(用于调整回答风格与详略,不要复述这段内容):\n" +
                profile.trim().take(4000)
        }
        return ChatMessage("system", prompt)
    }

    /** 取上下文:rounds<=0 时全部历史,否则最近 N 条(正序) */
    private suspend fun recentMessages(sessionId: Long, rounds: Int): List<MessageEntity> =
        (if (rounds <= 0) {
            database.messageDao().getAll(sessionId)
        } else {
            database.messageDao().getRecent(sessionId, rounds * 2).reversed()
        }).filter { m ->
            m.status != "error" && !(m.role == "assistant" && m.content.isBlank())
        }

    /** 把系统消息放到最前,历史原样保留 */
    private suspend fun withSystem(messages: List<ChatMessage>): List<ChatMessage> =
        listOf(systemMessage()) + messages

    /**
     * 组装上下文:rounds<=0 时携带全部历史(默认,直到模型上下文上限),
     * 否则只带最近 rounds 轮。新问题已入库,这里不重复追加。
     */
    private suspend fun buildContext(
        sessionId: Long,
        rounds: Int,
        images: List<ChatImage> = emptyList()
    ): List<ChatMessage> {
        val history = if (rounds <= 0) {
            database.messageDao().getAll(sessionId)
        } else {
            database.messageDao().getRecent(sessionId, rounds * 2).reversed()
        }
        val msgs = history.mapNotNull { m ->
            // 失败气泡只是 UI 提示,不能塞回模型上下文;空 assistant 消息也会被服务端拒绝
            if (m.status == "error") return@mapNotNull null
            if (m.role == "assistant" && m.content.isBlank()) return@mapNotNull null
            val attachments = buildString {
                val fromJson = runCatching { JSONArray(m.filesJson.orEmpty()) }.getOrNull()
                if (fromJson != null && fromJson.length() > 0) {
                    for (i in 0 until fromJson.length()) {
                        val o = fromJson.optJSONObject(i) ?: continue
                        append("\n\n【附件:${o.optString("name")}】\n${o.optString("text")}")
                    }
                } else if (!m.attachmentText.isNullOrBlank()) {
                    append("\n\n【附件:${m.attachmentName.orEmpty()}】\n${m.attachmentText}")
                }
            }
            ChatMessage(m.role, m.content + attachments, reasoning = m.reasoning)
        }.toMutableList()
        if (images.isNotEmpty() && msgs.isNotEmpty() && msgs.last().role == "user") {
            val last = msgs.removeAt(msgs.lastIndex)
            msgs.add(ChatMessage(last.role, last.content, images = images, reasoning = last.reasoning))
        }
        return withSystem(msgs)
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
        images: List<ChatImage> = emptyList(),
        files: List<ChatFile> = emptyList(),
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onUsage: (TokenUsage) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onInterrupted: (fullText: String, reasoning: String, message: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val now = System.currentTimeMillis()
        val isFirstMessage = database.messageDao().getFirst(sessionId) == null

        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = question,
                imageBase64 = images.firstOrNull()?.base64,
                imagesJson = imagesToJson(images),
                filesJson = filesToJson(files),
                attachmentName = files.firstOrNull()?.name,
                attachmentSize = files.firstOrNull()?.sizeBytes,
                attachmentText = files.firstOrNull()?.text,
                createdAt = now
            )
        )
        database.sessionDao().touch(sessionId, now)

        // 首条消息自动生成会话标题
        if (isFirstMessage) {
            val title = question.replace("\n", " ").take(20).ifBlank { "新会话" }
            database.sessionDao().rename(sessionId, title)
        }

        val messages = buildContext(sessionId, config.contextRounds, images)
        return apiClient.streamChat(
            config, messages, onDelta, onReasoning, onUsage, onComplete, onInterrupted, onError
        )
    }

    /** 断线重连:不重复入库,直接用库中历史(含最后一条用户消息)重发请求 */
    suspend fun retryStream(
        sessionId: Long,
        config: ModelConfig,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
        onUsage: (TokenUsage) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onInterrupted: (fullText: String, reasoning: String, message: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource {
        val messages = withSystem(recentMessages(sessionId, config.contextRounds).map {
            ChatMessage(it.role, it.content, reasoning = it.reasoning)
        })
        return apiClient.streamChat(
            config, messages, onDelta, onReasoning, onUsage, onComplete, onInterrupted, onError
        )
    }

    /** AI 回复完整入库(含思考过程与本轮用量) */
    suspend fun saveAssistantMessage(
        sessionId: Long,
        content: String,
        reasoning: String? = null,
        usageJson: String? = null
    ) {
        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "assistant",
                content = content,
                reasoning = reasoning,
                usageJson = usageJson,
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
        onUsage: (TokenUsage) -> Unit,
        onComplete: (fullText: String, reasoning: String) -> Unit,
        onInterrupted: (fullText: String, reasoning: String, message: String) -> Unit,
        onError: (String) -> Unit
    ): EventSource? {
        val userMessage = database.messageDao().getLastUserBefore(sessionId, assistantMessageId)
            ?: return null
        database.messageDao().deleteById(assistantMessageId)
        val messages = withSystem(recentMessages(sessionId, config.contextRounds).map {
            ChatMessage(it.role, it.content, reasoning = it.reasoning)
        })
        return apiClient.streamChat(
            config, messages, onDelta, onReasoning, onUsage, onComplete, onInterrupted, onError
        )
    }

    /** 非流式单次问答(用户消息已入库,调用方保存返回结果) */
    suspend fun sendMessageOnce(
        sessionId: Long,
        config: ModelConfig,
        question: String,
        images: List<ChatImage> = emptyList(),
        files: List<ChatFile> = emptyList()
    ): Result<ChatResult> {
        val now = System.currentTimeMillis()
        database.messageDao().insert(
            MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = question,
                imageBase64 = images.firstOrNull()?.base64,
                imagesJson = imagesToJson(images),
                filesJson = filesToJson(files),
                attachmentName = files.firstOrNull()?.name,
                attachmentSize = files.firstOrNull()?.sizeBytes,
                attachmentText = files.firstOrNull()?.text,
                createdAt = now
            )
        )
        database.sessionDao().touch(sessionId, now)
        val messages = buildContext(sessionId, config.contextRounds, images)
        return apiClient.chatOnce(config, messages)
    }

    suspend fun clearMessages(sessionId: Long) = database.messageDao().deleteBySession(sessionId)

    private fun imagesToJson(images: List<ChatImage>): String? {
        if (images.isEmpty()) return null
        val arr = JSONArray()
        images.forEach { img ->
            arr.put(org.json.JSONObject().apply {
                put("base64", img.base64)
                put("mime", img.mime)
            })
        }
        return arr.toString()
    }

    private fun filesToJson(files: List<ChatFile>): String? {
        if (files.isEmpty()) return null
        val arr = JSONArray()
        files.forEach { f ->
            arr.put(org.json.JSONObject().apply {
                put("name", f.name)
                put("size", f.sizeBytes)
                put("text", f.text)
            })
        }
        return arr.toString()
    }

    /** 整段会话的用量条目(JSON 字符串),统计用 */
    suspend fun sessionUsage(sessionId: Long): List<String> =
        database.messageDao().usageOfSession(sessionId)
}
