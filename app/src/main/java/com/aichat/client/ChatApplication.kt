package com.aichat.client

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.aichat.client.data.local.AppDatabase
import java.util.concurrent.ConcurrentHashMap
import com.aichat.client.data.remote.ChatApiClient
import com.aichat.client.data.remote.provider.ProviderApiClient
import com.aichat.client.data.repository.ChatRepository
import com.aichat.client.data.repository.SessionRepository
import com.aichat.client.data.settings.SettingsRepository

/**
 * 应用入口:轻量级服务定位器(MVP 阶段不引入 Hilt,后续可替换)。
 */
class ChatApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        // PDFBox 需要先加载字体等资源,否则首次解析 PDF 会失败
        PDFBoxResourceLoader.init(applicationContext)
        // 兜底清理匿名会话:进程被系统杀掉时 onCleared 来不及执行
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { sessionRepository.clearEphemeralSessions() }
        }
    }

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(this) }
    val sessionRepository: SessionRepository by lazy { SessionRepository(database) }
    val chatApiClient: ChatApiClient by lazy { ChatApiClient() }
    val providerApiClient: ProviderApiClient by lazy { ProviderApiClient() }
    val chatRepository: ChatRepository by lazy {
        ChatRepository(database, chatApiClient, settingsRepository)
    }

    /** 各会话的输入草稿(切页不丢失,进程存活期间有效) */
    val chatDrafts = ConcurrentHashMap<Long, String>()
}
