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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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

/** 设置页:多套模型配置管理 + 参数调节 + 测试连接 */
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

            // ---------- 参数调节 ----------
            Text("参数调节", style = MaterialTheme.typography.titleMedium)

            Text("温度 Temperature: %.1f".format(form.temperature))
            Slider(
                value = form.temperature,
                onValueChange = { v -> viewModel.updateForm { f -> f.copy(temperature = v) } },
                valueRange = 0f..2f
            )

            Text("TopP: %.2f".format(form.topP))
            Slider(
                value = form.topP,
                onValueChange = { v -> viewModel.updateForm { f -> f.copy(topP = v) } },
                valueRange = 0f..1f
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    label = "最大输出 tokens",
                    value = form.maxTokens,
                    onCommit = { v -> viewModel.updateForm { f -> f.copy(maxTokens = v) } },
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    label = "超时(秒)",
                    value = form.timeoutSeconds,
                    onCommit = { v -> viewModel.updateForm { f -> f.copy(timeoutSeconds = v) } },
                    modifier = Modifier.weight(1f)
                )
            }

            NumberField(
                label = "上下文记忆轮数",
                value = form.contextRounds,
                onCommit = { v -> viewModel.updateForm { f -> f.copy(contextRounds = v) } },
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

/** 数字输入框:本地文本状态,仅在合法数字时提交,避免编辑过程中被重置 */
@Composable
private fun NumberField(
    label: String,
    value: Int,
    onCommit: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            it.toIntOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}
