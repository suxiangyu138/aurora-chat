package com.aichat.client.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.aichat.client.ui.about.AboutScreen
import com.aichat.client.ui.chat.ChatScreen
import com.aichat.client.ui.home.HomeScreen
import com.aichat.client.ui.settings.SettingsScreen

/** 路由表 */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val CHAT = "chat/{sessionId}"
    fun chat(sessionId: Long) = "chat/$sessionId"
}

@Composable
fun AppNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Routes.HOME) {
        // 首页:会话列表
        composable(Routes.HOME) {
            HomeScreen(
                onOpenSession = { id -> navController.navigate(Routes.chat(id)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) }
            )
        }
        // 关于页:开发者信息
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        // 聊天页:按 sessionId 定位
        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
        ) { entry ->
            val sessionId = entry.arguments?.getLong("sessionId") ?: 0L
            ChatScreen(
                sessionId = sessionId,
                onBack = { navController.popBackStack() }
            )
        }
        // 设置页:模型配置
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
