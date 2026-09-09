package com.aichat.client.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aichat.client.BuildConfig
import com.aichat.client.R
import com.aichat.client.ui.theme.AuroraIndigo
import com.aichat.client.ui.theme.AuroraMagenta
import com.aichat.client.ui.theme.AuroraTeal

private const val DEV_NAME = "苏巷雨"
private const val DEV_EMAIL = "suxiangyu_dev@foxmail.com"
private const val DEV_GITHUB = "suxiangyu138"
private const val REPO_PATH = "$DEV_GITHUB/aurora-chat"
private const val GITHUB_URL = "https://github.com/$DEV_GITHUB"
private const val REPO_URL = "https://github.com/$REPO_PATH"

/** 极光渐变(图标/标题/头像共用一套,视觉上与桌面图标同源) */
private val AuroraBrush = Brush.linearGradient(
    listOf(AuroraTeal, AuroraIndigo, AuroraMagenta)
)

/** 关于页:品牌头图 + 开发者信息 + iQOO 使用引导 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    // 外部跳转统一兜底:设备上没有可处理的应用时给出提示,不静默失败
    fun openWeb(url: String) {
        if (!startExternal(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))) {
            clipboard.setText(AnnotatedString(url))
            Toast.makeText(context, "未找到浏览器,已复制链接", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))

            AppIcon(size = 104.dp)

            Spacer(Modifier.height(16.dp))
            Text(
                "Aurora Chat",
                style = MaterialTheme.typography.headlineMedium.merge(
                    TextStyle(
                        brush = AuroraBrush,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.4.sp
                    )
                )
            )
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    "v${BuildConfig.VERSION_NAME}" + if (BuildConfig.DEBUG) " · DEBUG" else "",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "一套模板,对话所有大模型",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "通用型自定义 AI 大模型聊天客户端",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(Icons.Outlined.Cloud, "无服务端")
                Tag(Icons.Outlined.Shield, "密钥硬件加密")
            }

            Spacer(Modifier.height(28.dp))

            SectionTitle("开发者")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(AuroraBrush),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            DEV_NAME.take(1),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(DEV_NAME, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "独立开发者 · Android / Kotlin",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    InfoRow(Icons.Outlined.MailOutline, "联系邮箱", DEV_EMAIL) {
                        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEV_EMAIL"))
                        if (!startExternal(context, intent)) {
                            clipboard.setText(AnnotatedString(DEV_EMAIL))
                            Toast.makeText(context, "未找到邮件应用,已复制邮箱", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    InfoRow(Icons.Outlined.Code, "GitHub", "@$DEV_GITHUB") { openWeb(GITHUB_URL) }
                    InfoRow(Icons.Outlined.Star, "开源仓库", REPO_PATH) { openWeb(REPO_URL) }
                    InfoRow(Icons.Outlined.Smartphone, "深度适配", "iQOO 全系(OriginOS)")
                }
            }

            Spacer(Modifier.height(24.dp))

            SectionTitle("iQOO 后台保活设置")
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                )
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "为保证长回答不被系统中断,建议完成以下四项:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    StepRow(1, "设置 → 应用与权限 → 应用管理 → Aurora Chat → 允许自启动")
                    StepRow(2, "设置 → 电池 → 后台耗电管理 → Aurora Chat → 允许后台高耗电")
                    StepRow(3, "最近任务界面下拉锁定 Aurora Chat,防止误清理")
                    StepRow(4, "允许通知:流式回答期间显示保活通知")
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "Kotlin · Jetpack Compose · Material 3",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "© 2026 $DEV_NAME · 保留所有权利",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

/**
 * 应用图标:直接叠加自适应图标的两层(背景 + 前景),与桌面图标同源 ——
 * 不要再用 drawable/ic_launcher(那是通知栏用的 24dp 单色图标,和桌面图标是两回事)。
 * 两层整体放大 1.5 倍是还原 108→72 的自适应裁切比例:桌面只显示中心 72x72 区域,
 * 不放大则前景小一圈、背景渐变还会多露出一段边角色,和桌面看着不像同一个图标。
 */
@Composable
private fun AppIcon(size: Dp) {
    Surface(shape = RoundedCornerShape(size / 4), shadowElevation = 6.dp) {
        Box(Modifier.size(size).scale(1.5f)) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_background),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "应用图标",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
    )
}

/** 头图下方的特性标签 */
@Composable
private fun Tag(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** 信息行:传了 onClick 即可点击跳转,右侧带外链标识 */
@Composable
private fun InfoRow(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    val rowModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    Row(
        modifier = rowModifier.padding(horizontal = 6.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        if (onClick != null) {
            Icon(
                Icons.Outlined.OpenInNew,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 带序号的引导步骤 */
@Composable
private fun StepRow(index: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("$index", style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 拉起外部应用;没有可处理的应用时返回 false,由调用方兜底 */
private fun startExternal(context: Context, intent: Intent): Boolean =
    runCatching { context.startActivity(intent); true }.getOrDefault(false)
