package com.aichat.client

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.aichat.client.ui.AppRoot

class MainActivity : ComponentActivity() {

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 保活前台服务的通知即使被拒也能运行,无需处理结果 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边显示:沉浸式状态栏/导航栏(适配 iQOO 高刷全面屏)
        enableEdgeToEdge()
        // 申请通知权限(Android 13+,用于对话保活通知)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            AppRoot()
        }
    }
}
