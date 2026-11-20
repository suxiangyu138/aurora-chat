package com.aichat.client.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.remote.provider.ProviderId
import com.aichat.client.data.remote.provider.TestKind
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.ModelPresets
import com.aichat.client.data.settings.ThemeMode

/** 设置页:模型配置管理 + 模板 + 拉取模型 + 测试连接 + 外观与备份 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val activeConfig by viewModel.activeConfig.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val testing by viewModel.testing.collectAsStateWithLifecycle()
    val testOutcome by viewModel.testOutcome.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val modelsLoading by viewModel.modelsLoading.collectAsStateWithLifecycle()
    val modelsError by viewModel.modelsError.collectAsStateWithLifecycle()
    val modelsFromApi by viewModel.modelsFromApi.collectAsStateWithLifecycle()
    val profileEnabled by viewModel.profileEnabled.collectAsStateWithLifecycle()
    val profileText by viewModel.profileText.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val accentArgb by viewModel.accentArgb.collectAsStateWithLifecycle()
    val exportIntent by viewModel.exportIntent.collectAsStateWithLifecycle()
    val backupResult by viewModel.backupResult.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var clearConfirm by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importConfigs(it) } }

    LaunchedEffect(Unit) { viewModel.loadActiveIntoFormIfEmpty() }

    LaunchedEffect(exportIntent) {
        exportIntent?.let {
            context.startActivity(it)
            viewModel.consumeExportIntent()
        }
    }

    val provider = ProviderId.fromKey(form.providerId)
    val providerLabel = if (provider == ProviderId.CUSTOM) "自定义 / 中转站" else provider.label

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模型配置") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---------- 已保存配置 ----------
            if (configs.isNotEmpty()) {
                Text("已保存的配置", style = MaterialTheme.typography.titleMedium)
                configs.forEach { config ->
                    val isActive = config.id == activeConfig?.id
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setActive(config.id)
                                    viewModel.loadIntoForm(config)
                                }
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = isActive, onClick = null)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    config.name.ifBlank { config.modelName.ifBlank { "未命名配置" } },
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "${ProviderId.fromKey(config.providerId).let { if (it == ProviderId.CUSTOM) "自定义" else it.label }} · ${config.modelName.ifBlank { "未填模型" }}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.delete(config.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除配置")
                            }
                        }
                    }
                }
            }

            // ---------- 模板 ----------
            Text("模板", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { presetMenuOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("选择厂商或中转站模板(URL 自动填好)")
            }
            DropdownMenu(
                expanded = presetMenuOpen,
                onDismissRequest = { presetMenuOpen = false }
            ) {
                ModelPresets.list.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(
                                    "${preset.name}${if (preset.model.isNotBlank()) "  ·  ${preset.model}" else ""}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (preset.hint.isNotBlank()) {
                                    Text(
                                        preset.hint,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        onClick = {
                            viewModel.applyPreset(preset)
                            presetMenuOpen = false
                        }
                    )
                }
            }

            // ---------- 接口配置 ----------
            Text("接口配置", style = MaterialTheme.typography.titleMedium)
            Text(
                "当前模板:$providerLabel。中转站或自建兼容接口选最后一个模板,填地址与 Key 后可直接拉取模型。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = form.name,
                onValueChange = { viewModel.updateForm { f -> f.copy(name = it) } },
                label = { Text("配置名称") },
                placeholder = { Text("留空自动按「厂商 · 模型」命名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = { viewModel.updateBaseUrl(it) },
                label = { Text("接口 Base URL") },
                placeholder = { Text("https://api.example.com/v1") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = form.apiKey,
                onValueChange = { viewModel.updateForm { f -> f.copy(apiKey = it) } },
                label = { Text("API Key") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (apiKeyVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "显示/隐藏密钥"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.fetchModels() },
                    enabled = !modelsLoading,
                    modifier = Modifier.weight(1f)
                ) {
                    if (modelsLoading) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(if (modelsLoading) "拉取中…" else "从接口拉取模型")
                }
                if (modelsFromApi) {
                    Text(
                        "已拉取 ${models.size} 个",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            modelsError?.let { error ->
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
                Row {
                    TextButton(onClick = { viewModel.fetchModels() }) { Text("重试") }
                    TextButton(onClick = { viewModel.consumeModelsError() }) { Text("忽略") }
                }
            }

            // ---------- 模型列表 ----------
            if (models.isNotEmpty()) {
                Text("可用模型", style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        models.forEach { model ->
                            val selected = form.modelName == model.id
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.selectModel(model) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Column(Modifier.weight(1f)) {
                                    Text(model.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        model.subtitle.ifBlank { model.id },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.modelName,
                onValueChange = { viewModel.updateForm { f -> f.copy(modelName = it) } },
                label = { Text("模型 ID(可手动填写)") },
                placeholder = { Text("deepseek-flash") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- 高级参数(默认收起) ----------
            TextButton(onClick = { advancedOpen = !advancedOpen }) {
                Text(if (advancedOpen) "收起高级设置" else "高级设置(温度、输出上限、上下文与请求模板)")
            }

            if (advancedOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatField(
                        label = "温度(0~2)",
                        value = form.temperature,
                        range = 0f..2f,
                        onCommit = { v -> viewModel.updateForm { f -> f.copy(temperature = v) } },
                        modifier = Modifier.weight(1f)
                    )
                    FloatField(
                        label = "TopP(0~1)",
                        value = form.topP,
                        range = 0f..1f,
                        onCommit = { v -> viewModel.updateForm { f -> f.copy(topP = v) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(
                        label = "最大输出 tokens",
                        value = form.maxTokens,
                        range = 1..32768,
                        onCommit = { v -> viewModel.updateForm { f -> f.copy(maxTokens = v) } },
                        modifier = Modifier.weight(1f)
                    )
                    NumberField(
                        label = "超时(秒)",
                        value = form.timeoutSeconds,
                        range = 10..600,
                        onCommit = { v -> viewModel.updateForm { f -> f.copy(timeoutSeconds = v) } },
                        modifier = Modifier.weight(1f)
                    )
                }

                NumberField(
                    label = "上下文记忆轮数",
                    value = form.contextRounds,
                    range = 0..50,
                    onCommit = { v -> viewModel.updateForm { f -> f.copy(contextRounds = v) } },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("流式输出(打字机效果)")
                        Text(
                            "关闭后使用非流式单次问答",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = form.streamEnabled,
                        onCheckedChange = { v -> viewModel.updateForm { f -> f.copy(streamEnabled = v) } }
                    )
                }

                OutlinedTextField(
                    value = form.customBody,
                    onValueChange = { viewModel.updateForm { f -> f.copy(customBody = it) } },
                    label = { Text("自定义请求体模板(可选)") },
                    placeholder = {
                        Text(
                            "留空使用默认模板。支持占位符:\n" +
                                "{model} {messages} {temperature} {top_p} {max_tokens} {stream}"
                        )
                    },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ---------- 保存与测试 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("保存")
                }
                OutlinedButton(
                    onClick = { viewModel.resetForm() },
                    modifier = Modifier.weight(1f)
                ) { Text("新建") }
                Button(
                    onClick = { viewModel.testConnection() },
                    enabled = !testing,
                    modifier = Modifier.weight(1f)
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text("测试连接")
                    }
                }
            }

            // ---------- 个性化画像 ----------
            Text("个性化", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("把个人画像注入每次对话")
                    Text(
                        "开启后,每轮请求会附带这里的背景与输出偏好;关闭则完全不带。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = profileEnabled,
                    onCheckedChange = { viewModel.setProfileEnabled(it) }
                )
            }

            OutlinedTextField(
                value = profileText,
                onValueChange = { viewModel.setProfileText(it) },
                label = { Text("个人背景与输出偏好") },
                placeholder = {
                    Text(
                        "例:计算机专业大三,准备 Java 后端与大模型应用岗位;" +
                            "回答先给结论再给步骤,代码用 Kotlin/Java,少用客套话,术语保留英文。"
                    )
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- 外观 ----------
            Text("外观", style = MaterialTheme.typography.titleMedium)

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val modes = ThemeMode.entries
                modes.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = modes.size)
                    ) {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            }
                        )
                    }
                }
            }

            Text("主题色", style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AccentPresets.forEach { (name, color) ->
                    val selected = color.toArgb() == accentArgb
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = CircleShape
                            )
                            .clickable { viewModel.setAccent(color.toArgb()) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selected) Text("✓", color = Color.White)
                    }
                }
            }

            // ---------- 备份与恢复 ----------
            Text("备份与恢复", style = MaterialTheme.typography.titleMedium)
            Text(
                "导出会生成包含 API Key 的明文 JSON 文件,请妥善保管,勿公开分享。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { viewModel.exportConfigs() },
                    modifier = Modifier.weight(1f)
                ) { Text("导出配置") }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    modifier = Modifier.weight(1f)
                ) { Text("导入配置") }
                OutlinedButton(
                    onClick = { clearConfirm = true },
                    modifier = Modifier.weight(1f)
                ) { Text("清空配置") }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    // 测试结果对话框
    testOutcome?.let { outcome ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTestResult() },
            title = { Text(testTitle(outcome.kind)) },
            text = {
                Column {
                    Text(outcome.message)
                    if (outcome.latencyMs > 0) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "耗时 ${outcome.latencyMs} ms",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeTestResult() }) { Text("知道了") }
            }
        )
    }

    // 备份操作结果对话框
    backupResult?.let { (ok, message) ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeBackupResult() },
            title = { Text(if (ok) "操作成功" else "操作失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeBackupResult() }) { Text("知道了") }
            }
        )
    }

    // 清空配置确认对话框
    if (clearConfirm) {
        AlertDialog(
            onDismissRequest = { clearConfirm = false },
            title = { Text("清空所有配置") },
            text = { Text("将删除全部模型配置(会话记录不受影响),确定继续?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAllConfigs()
                    clearConfirm = false
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 测试结论标题:把失败类型说清楚,不只显示「失败」 */
private fun testTitle(kind: TestKind): String = when (kind) {
    TestKind.OK -> "连接成功"
    TestKind.AUTH -> "鉴权失败"
    TestKind.URL_NOT_FOUND -> "接口地址不存在"
    TestKind.MODEL_NOT_FOUND -> "模型不存在"
    TestKind.RATE_LIMIT -> "限流或额度不足"
    TestKind.SERVER -> "服务端错误"
    TestKind.TIMEOUT -> "连接超时"
    TestKind.NETWORK -> "网络异常"
    TestKind.INVALID -> "配置不完整"
    TestKind.UNKNOWN -> "连接失败"
}

/** 主题色预设 */
private val AccentPresets = listOf(
    "靛蓝" to Color(0xFF3F51B5),
    "海洋蓝" to Color(0xFF1976D2),
    "青绿" to Color(0xFF009688),
    "翠绿" to Color(0xFF43A047),
    "橙" to Color(0xFFF57C00),
    "玫粉" to Color(0xFFD81B60),
    "紫" to Color(0xFF8E24AA)
)

/** 浮点输入框:本地文本状态,仅合法且在范围内时提交,防止 NaN/越界参数 */
@Composable
private fun FloatField(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toFloatOrNull()
                ?.takeIf { v -> v.isFinite() && v in range }
                ?.let(onCommit)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

/** 整数输入框:本地文本状态,仅合法且在范围内时提交 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    range: IntRange,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.takeIf { v -> v in range }?.let(onCommit)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}
