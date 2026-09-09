package com.aichat.client.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

private fun buildDarkScheme(accent: Color) = darkColorScheme(
    primary = lerp(accent, Color.White, 0.25f),
    secondary = IndigoGrey80,
    tertiary = Indigo80
)

private fun buildLightScheme(accent: Color) = lightColorScheme(
    primary = accent,
    secondary = IndigoGrey40,
    tertiary = Indigo400
)

/**
 * 应用主题:深色模式三态(跟随系统/浅色/深色)+ 主题色定制。
 */
@Composable
fun AIChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accent: Color = Indigo500,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) buildDarkScheme(accent) else buildLightScheme(accent)
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
