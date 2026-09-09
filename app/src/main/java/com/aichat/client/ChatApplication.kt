package com.aichat.client

import android.app.Application
import com.aichat.client.data.local.AppDatabase
import java.util.concurrent.ConcurrentHashMap
import com.aichat.client.data.remote.ChatApiClient
import com.aichat.client.data.repository.ChatRepository
import com.aichat.client.data.repository.SessionRepository
import com.aichat.client.data.settings.SettingsRepository

/**
 * 应用入口:轻量级服务定位器(MVP 阶段不引入 Hilt,后续可替换)。
 */
class ChatApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }
    val chatApiClient: ChatApiClient by lazy { ChatApiClient() }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(database, chatApiClient, settingsRepository)
    }

    /** 各会话的输入草稿(切页不丢失,进程存活期间有效) */
    val chatDrafts = ConcurrentHashMap<Long, String>()
}
