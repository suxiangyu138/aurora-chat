package com.aichat.client.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 会话表 */
@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    /** 匿名会话:退出即删除,不进入列表,不参与任何持久化统计 */
    val ephemeral: Boolean = false
)

/** 消息表(会话删除时级联删除消息) */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("sessionId")]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String,   // "user" / "assistant"
    val content: String,
    /** 思考过程(DeepSeek-R1/GLM 等模型的 reasoning_content,无则 null) */
    val reasoning: String? = null,
    /** 消息状态:"done" 正常 / "error" 失败(展示重试按钮) */
    val status: String = "done",
    /** 用户消息携带的图片(base64 压缩后存储,气泡内独立展示) */
    val imageBase64: String? = null,
    /** 本轮用量 JSON:{prompt,completion,cached,reasoning,total,cost};仅 AI 消息有 */
    val usageJson: String? = null,
    /** 多图展示:JSON 数组 [{base64,mime}];历史轮次不重新发送 */
    val imagesJson: String? = null,
    /** 多文件展示与解析:JSON 数组 [{name,size,text}] */
    val filesJson: String? = null,
    /** 用户消息附带的文档:名称、字节数、抽取出的正文 */
    val attachmentName: String? = null,
    val attachmentSize: Long? = null,
    val attachmentText: String? = null,
    val createdAt: Long
) {
    val isError: Boolean get() = status == "error"
}
