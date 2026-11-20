package com.aichat.client.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    /** 首页列表:匿名会话永不出现 */
    @Query("SELECT * FROM sessions WHERE ephemeral = 0 ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE id = :id")
    fun observeById(id: Long): Flow<SessionEntity?>

    @Insert
    suspend fun insert(session: SessionEntity): Long

    @Update
    suspend fun update(session: SessionEntity)

    @Query("UPDATE sessions SET title = :title WHERE id = :id")
    suspend fun rename(id: Long, title: String)

    @Query("UPDATE sessions SET updatedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)

    @Query("DELETE FROM sessions WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 批量删除会话(消息按外键级联删除) */
    @Query("DELETE FROM sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    /** 清理所有匿名会话(启动时兜底:上次异常退出没来得及删) */
    @Query("DELETE FROM sessions WHERE ephemeral = 1")
    suspend fun deleteAllEphemeral()
}

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC")
    fun observeBySession(sessionId: Long): Flow<List<MessageEntity>>

    /** 全部消息(按时间正序),上下文不限轮数时使用 */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC, id ASC")
    suspend fun getAll(sessionId: Long): List<MessageEntity>

    /** 最近 N 条(按时间倒序),用于组装上下文 */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(sessionId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt ASC LIMIT 1")
    suspend fun getFirst(sessionId: Long): MessageEntity?

    /** 分页:观察最新 N 条(倒序,展示时反转)——历史消息懒加载 */
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY createdAt DESC, id DESC LIMIT :limit")
    fun observeLatest(sessionId: Long, limit: Int): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE sessionId = :sessionId")
    suspend fun count(sessionId: Long): Int

    /** 整段会话的用量记录(按时间正序),用于统计累计 token 与花费 */
    @Query("SELECT usageJson FROM messages WHERE sessionId = :sessionId AND usageJson IS NOT NULL ORDER BY createdAt ASC, id ASC")
    suspend fun usageOfSession(sessionId: Long): List<String>

    @Insert
    suspend fun insert(message: MessageEntity): Long

    @Query("DELETE FROM messages WHERE sessionId = :sessionId")
    suspend fun deleteBySession(sessionId: Long)

    /** 删除单条消息 */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** 更新消息内容(编辑用户消息用) */
    @Query("UPDATE messages SET content = :content WHERE id = :id")
    suspend fun updateContent(id: Long, content: String)

    /** 删除某消息及其之后的所有消息(编辑重发用) */
    @Query("DELETE FROM messages WHERE sessionId = :sessionId AND id >= :fromId")
    suspend fun deleteFrom(sessionId: Long, fromId: Long)

    /** 找某消息之前的最近一条用户消息(重新生成/重试用) */
    @Query(
        "SELECT * FROM messages WHERE sessionId = :sessionId AND id < :beforeId AND role = 'user' " +
            "ORDER BY createdAt DESC, id DESC LIMIT 1"
    )
    suspend fun getLastUserBefore(sessionId: Long, beforeId: Long): MessageEntity?
}
