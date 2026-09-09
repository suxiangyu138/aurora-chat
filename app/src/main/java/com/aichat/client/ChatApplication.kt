package com.aichat.client

import android.app.Application
import com.aichat.client.data.local.AppDatabase
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
}
