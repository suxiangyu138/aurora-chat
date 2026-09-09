package com.aichat.client.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aichat.client.ChatApplication
import com.aichat.client.data.settings.ThemeMode
import com.aichat.client.ui.navigation.AppNavHost
import com.aichat.client.ui.theme.AIChatTheme

/** 应用根:读取用户主题设置(深色模式三态 + 主题色)并应用 */
@Composable
fun AppRoot() {
    val context = LocalContext.current
    val repository = remember {
        (context.applicationContext as ChatApplication).settingsRepository
    }
    val themeMode by repository.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
    val accentArgb by repository.accentColor.collectAsStateWithLifecycle(initialValue = 0xFF3F51B5.toInt())

    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    AIChatTheme(darkTheme = darkTheme, accent = Color(accentArgb)) {
        AppNavHost()
    }
}
