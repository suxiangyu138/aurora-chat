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
    val updatedAt: Long
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
    val createdAt: Long
) {
    val isError: Boolean get() = status == "error"
}
