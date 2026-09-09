package com.aichat.client.data.repository

import com.aichat.client.data.local.AppDatabase
import com.aichat.client.data.local.MessageEntity
import com.aichat.client.data.local.SessionEntity
import kotlinx.coroutines.flow.Flow

/** 会话/消息的本地数据仓库 */
class SessionRepository(private val database: AppDatabase) {

    fun observeSessions(): Flow<List<SessionEntity>> = database.sessionDao().observeAll()

    fun observeSession(id: Long): Flow<SessionEntity?> = database.sessionDao().observeById(id)

    fun observeMessages(sessionId: Long): Flow<List<MessageEntity>> =
        database.messageDao().observeBySession(sessionId)

    suspend fun createSession(title: String = "新会话"): Long {
        val now = System.currentTimeMillis()
        return database.sessionDao().insert(
            SessionEntity(title = title, createdAt = now, updatedAt = now)
        )
    }

    suspend fun renameSession(id: Long, title: String) = database.sessionDao().rename(id, title)

    suspend fun deleteSession(id: Long) = database.sessionDao().deleteById(id)

    suspend fun clearMessages(sessionId: Long) = database.messageDao().deleteBySession(sessionId)
}
