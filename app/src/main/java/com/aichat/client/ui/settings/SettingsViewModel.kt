package com.aichat.client.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.client.ChatApplication
import com.aichat.client.data.remote.ChatMessage
import com.aichat.client.data.settings.ModelConfig
import com.aichat.client.data.settings.ModelPresets
import com.aichat.client.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
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

    // ---------- 外观设置 ----------

    val themeMode: StateFlow<ThemeMode> = settingsRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val accentArgb: StateFlow<Int> = settingsRepository.accentColor
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0xFF3F51B5.toInt())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setAccent(argb: Int) {
        viewModelScope.launch { settingsRepository.setAccentColor(argb) }
    }

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

    // ---------- 备份导入导出 ----------

    /** 待启动的分享 Intent(导出配置) */
    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()

    /** (成功, 消息) 的备份操作结果 */
    private val _backupResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val backupResult: StateFlow<Pair<Boolean, String>?> = _backupResult.asStateFlow()

    /** 备份目录:导出文件含明文 API Key,必须用完尽快清理 */
    private fun backupDir(app: Application) = File(app.cacheDir, "backup")

    /** 清掉历史备份文件(上次导出残留、或用户在分享面板直接取消的情况) */
    private fun clearBackupCache(app: Application) {
        runCatching { backupDir(app).listFiles()?.forEach { it.delete() } }
    }

    init {
        // 进入设置页先清一次:上次导出的明文密钥不应跨会话留在磁盘上
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { clearBackupCache(app) }
    }

    fun exportConfigs() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    // 文件名带时间戳:固定路径会让曾被授权的应用在授权存续期内反复读到新备份
                    val dir = backupDir(app)
                    clearBackupCache(app)
                    dir.mkdirs()
                    File(dir, "configs_backup_${System.currentTimeMillis()}.json")
                        .apply { writeText(settingsRepository.exportConfigs()) }
                }
            }
            file.onSuccess {
                val uri = FileProvider.getUriForFile(app, "com.aichat.client.fileprovider", it)
                val share = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _exportIntent.value = Intent.createChooser(share, "导出配置备份")
            }.onFailure {
                _backupResult.value = false to "导出失败:${it.message}"
            }
        }
    }

    fun consumeExportIntent() {
        _exportIntent.value = null
    }

    fun importConfigs(uri: Uri) {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val json = runCatching {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            }.getOrNull()
            if (json.isNullOrBlank()) {
                _backupResult.value = false to "读取文件失败"
                return@launch
            }
            val count = settingsRepository.importConfigs(json)
            _backupResult.value = if (count > 0) true to "导入成功:共 $count 套配置"
            else false to "未在文件中找到有效配置"
        }
    }

    fun consumeBackupResult() {
        _backupResult.value = null
    }

    fun clearAllConfigs() {
        viewModelScope.launch {
            settingsRepository.clearAllConfigs()
            _backupResult.value = true to "已清空所有配置"
        }
    }
}
