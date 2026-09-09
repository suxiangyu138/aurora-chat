package com.aichat.client.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.client.ChatApplication
import com.aichat.client.data.remote.ChatMessage
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.ModelPresets
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepository = (application as ChatApplication).settingsRepository
    private val apiClient = (application as ChatApplication).chatApiClient

    val configs: StateFlow<List<ModelConfig>> = settingsRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConfig: StateFlow<ModelConfig?> = settingsRepository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 表单状态(正在编辑的配置;空 id 表示新建) */
    private val _form = MutableStateFlow(ModelConfig())
    val form: StateFlow<ModelConfig> = _form.asStateFlow()

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    /** (成功, 消息) 的测试结果;null 表示无结果 */
    private val _testResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val testResult: StateFlow<Pair<Boolean, String>?> = _testResult.asStateFlow()

    /** 打开页面时,表单为空则载入当前生效配置 */
    fun loadActiveIntoFormIfEmpty() {
        viewModelScope.launch {
            if (_form.value.baseUrl.isBlank() && _form.value.apiKey.isBlank()) {
                settingsRepository.getActiveConfig()?.let { _form.value = it }
            }
        }
    }

    fun updateForm(transform: (ModelConfig) -> ModelConfig) {
        _form.value = transform(_form.value)
    }

    fun resetForm() {
        _form.value = ModelConfig()
    }

    fun loadIntoForm(config: ModelConfig) {
        _form.value = config
    }

    /** 应用预设模板:一键填充 URL 与模型名(API Key 由用户填写) */
    fun applyPreset(preset: ModelPresets.Preset) {
        _form.value = _form.value.copy(baseUrl = preset.baseUrl, modelName = preset.model)
    }

    /** 保存表单:有 id 则更新,否则新建并设为生效 */
    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val config = f.copy(
                id = f.id.ifBlank { UUID.randomUUID().toString() },
                name = f.name.ifBlank { f.modelName.ifBlank { "配置 ${configs.value.size + 1}" } }
            )
            settingsRepository.saveConfig(config)
            _form.value = config
        }
    }

    fun delete(id: String) {
        viewModelScope.launch { settingsRepository.deleteConfig(id) }
    }

    fun setActive(id: String) {
        viewModelScope.launch { settingsRepository.setActive(id) }
    }

    /** 测试连接:非流式发送"你好",验证 URL/密钥/模型可用性 */
    fun testConnection() {
        if (_testing.value) return
        viewModelScope.launch {
            val config = _form.value
            if (!config.isComplete) {
                _testResult.value = false to "请先填写接口 URL、API Key、模型名称"
                return@launch
            }
            _testing.value = true
            val result = apiClient.chatOnce(
                config.copy(maxTokens = 16),
                listOf(ChatMessage("user", "你好"))
            )
            _testing.value = false
            _testResult.value = result.fold(
                onSuccess = { true to "连接成功:\n${it.content.take(120)}" },
                onFailure = { false to "连接失败:\n${it.message}" }
            )
        }
    }

    fun consumeTestResult() {
        _testResult.value = null
    }
}
