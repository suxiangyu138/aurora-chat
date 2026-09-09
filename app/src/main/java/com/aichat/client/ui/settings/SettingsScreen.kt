package com.aichat.client.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.ModelPresets

/** 设置页:多套模型配置管理 + 数值参数输入 + 预设模板 + 测试连接 */
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
    val testResult by viewModel.testResult.collectAsStateWithLifecycle()
    var apiKeyVisible by remember { mutableStateOf(false) }
    var presetMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.loadActiveIntoFormIfEmpty() }

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
                                Text(config.name, fontWeight = FontWeight.Medium)
                                Text(
                                    config.baseUrl,
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

            // ---------- 预设模板 ----------
            Text("预设模型模板", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = { presetMenuOpen = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("一键填充 URL 与模型名(密钥仍由你填写)")
            }
            DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                ModelPresets.list.forEach { preset ->
                    DropdownMenuItem(
                        text = { Text("${preset.name}  ·  ${preset.model.ifBlank { "模型名自填" }}") },
                        onClick = {
                            viewModel.applyPreset(preset)
                            presetMenuOpen = false
                        }
                    )
                }
            }

            // ---------- 接口配置表单 ----------
            Text("接口配置", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = form.baseUrl,
                onValueChange = { viewModel.updateForm { f -> f.copy(baseUrl = it) } },
                label = { Text("接口 URL") },
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

            OutlinedTextField(
                value = form.modelName,
                onValueChange = { viewModel.updateForm { f -> f.copy(modelName = it) } },
                label = { Text("模型名称") },
                placeholder = { Text("deepseek-chat / glm-4 / gpt-4o") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // ---------- 参数调节(手动输入数值) ----------
            Text("参数调节(手动输入数值)", style = MaterialTheme.typography.titleMedium)

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

            // ---------- 高级选项 ----------
            Text("高级选项", style = MaterialTheme.typography.titleMedium)

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

            // ---------- 操作按钮 ----------
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("保存配置")
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
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("测试连接")
                    }
                }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    // 测试结果对话框
    testResult?.let { (ok, message) ->
        AlertDialog(
            onDismissRequest = { viewModel.consumeTestResult() },
            title = { Text(if (ok) "测试成功" else "测试失败") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeTestResult() }) { Text("知道了") }
            }
        )
    }
}

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
