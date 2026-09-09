package com.aichat.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = Indigo200,
    secondary = IndigoGrey80,
    tertiary = Indigo80
)

private val LightColorScheme = lightColorScheme(
    primary = Indigo500,
    secondary = IndigoGrey40,
    tertiary = Indigo400
)

/**
 * 应用主题:跟随系统深色/浅色模式(OriginOS 深色模式自动适配)。
 */
@Composable
fun AIChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
