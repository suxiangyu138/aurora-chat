package com.aichat.client

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aichat.client.ui.AppRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边显示:沉浸式状态栏/导航栏(适配 iQOO 高刷全面屏)
        enableEdgeToEdge()
        setContent {
            AppRoot()
        }
    }
}
