package com.aichat.client.ui.settings

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.aichat.client.ChatApplication
import com.aichat.client.data.remote.provider.ModelInfo
import com.aichat.client.data.remote.provider.ProviderCatalog
import com.aichat.client.data.remote.provider.ProviderId
import com.aichat.client.data.remote.provider.TestKind
import com.aichat.client.data.remote.provider.TestOutcome
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
    private val providerApiClient = (application as ChatApplication).providerApiClient

    val configs: StateFlow<List<ModelConfig>> = settingsRepository.allConfigs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeConfig: StateFlow<ModelConfig?> = settingsRepository.activeConfig
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** 表单状态(正在编辑的配置;空 id 表示新建) */
    private val _form = MutableStateFlow(ModelConfig())
    val form: StateFlow<ModelConfig> = _form.asStateFlow()

    /** 当前表单可选的模型列表:内置清单 + 从接口拉取的结果 */
    private val _models = MutableStateFlow<List<ModelInfo>>(emptyList())
    val models: StateFlow<List<ModelInfo>> = _models.asStateFlow()

    private val _modelsLoading = MutableStateFlow(false)
    val modelsLoading: StateFlow<Boolean> = _modelsLoading.asStateFlow()

    /** 拉取失败原因(重试入口的提示文本) */
    private val _modelsError = MutableStateFlow<String?>(null)
    val modelsError: StateFlow<String?> = _modelsError.asStateFlow()

    private val _modelsFromApi = MutableStateFlow(false)
    val modelsFromApi: StateFlow<Boolean> = _modelsFromApi.asStateFlow()

    // ---------- 聊天字号 ----------

    val chatFontSize: StateFlow<Int> = settingsRepository.chatFontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    fun setChatFontSize(sizeSp: Int) {
        viewModelScope.launch { settingsRepository.setChatFontSize(sizeSp) }
    }

    // ---------- 个性化画像 ----------

    val profileEnabled: StateFlow<Boolean> = settingsRepository.profileEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val profileText: StateFlow<String> = settingsRepository.profileText
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    fun setProfileEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setProfileEnabled(enabled) }
    }

    fun setProfileText(text: String) {
        viewModelScope.launch { settingsRepository.setProfileText(text) }
    }

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

    // ---------- 测试连接 ----------

    private val _testing = MutableStateFlow(false)
    val testing: StateFlow<Boolean> = _testing.asStateFlow()

    /** 分类后的测试结果;null 表示无结果 */
    private val _testOutcome = MutableStateFlow<TestOutcome?>(null)
    val testOutcome: StateFlow<TestOutcome?> = _testOutcome.asStateFlow()

    fun consumeTestResult() {
        _testOutcome.value = null
    }

    // ---------- 表单 ----------

    /** 打开页面时,表单为空则载入当前生效配置 */
    fun loadActiveIntoFormIfEmpty() {
        viewModelScope.launch {
            if (_form.value.baseUrl.isBlank() && _form.value.apiKey.isBlank()) {
                settingsRepository.getActiveConfig()?.let { config ->
                    _form.value = config
                    refreshModelCandidates()
                }
            }
        }
    }

    fun updateForm(transform: (ModelConfig) -> ModelConfig) {
        _form.value = transform(_form.value)
    }

    /** 改接口地址:按域名自动识别厂商,中转站保持 custom,并清掉上一家的模型列表 */
    fun updateBaseUrl(url: String) {
        val detected = ProviderCatalog.detectProvider(url)
        val current = ProviderId.fromKey(_form.value.providerId)
        val provider = if (detected != ProviderId.CUSTOM) detected else current
        _form.value = _form.value.copy(baseUrl = url, providerId = provider.key)
        refreshModelCandidates()
    }

    fun resetForm() {
        _form.value = ModelConfig()
        refreshModelCandidates()
    }

    fun loadIntoForm(config: ModelConfig) {
        _form.value = config
        refreshModelCandidates()
    }

    /** 应用预设模板:填充接口地址、厂商标识与模型名 */
    fun applyPreset(preset: ModelPresets.Preset) {
        _form.value = _form.value.copy(
            baseUrl = preset.baseUrl,
            modelName = preset.model,
            providerId = preset.provider.key
        )
        _modelsFromApi.value = false
        _models.value = ProviderCatalog.builtinModels(preset.provider)
        _modelsError.value = null
    }

    private fun refreshModelCandidates() {
        val builtin = ProviderCatalog.builtinModels(
            ProviderId.fromKey(_form.value.providerId)
        )
        _models.value = builtin
        _modelsFromApi.value = false
        _modelsError.value = null
    }

    fun selectModel(model: ModelInfo) {
        _form.value = _form.value.copy(
            modelName = model.id,
            providerId = model.provider.key
        )
    }

    /** 从接口拉取该地址下的全部可用模型(厂商官方或中转站均可) */
    fun fetchModels() {
        if (_modelsLoading.value) return
        viewModelScope.launch {
            val config = _form.value
            _modelsLoading.value = true
            _modelsError.value = null
            val result = providerApiClient.fetchModels(config)
            _modelsLoading.value = false
            result.fold(
                onSuccess = { fetched ->
                    _models.value = fetched.models
                    _modelsFromApi.value = true
                    if (_form.value.modelName.isBlank()) {
                        fetched.models.firstOrNull()?.let { selectModel(it) }
                    }
                },
                onFailure = { e ->
                    _modelsError.value = e.message ?: "拉取失败"
                    _models.value = ProviderCatalog.builtinModels(
                        ProviderId.fromKey(_form.value.providerId)
                    )
                }
            )
        }
    }

    fun consumeModelsError() {
        _modelsError.value = null
    }

    /** 保存表单:有 id 则更新,否则新建并设为生效 */
    fun save() {
        viewModelScope.launch {
            val f = _form.value
            val providerLabel = ProviderId.fromKey(f.providerId)
                .let { if (it == ProviderId.CUSTOM) "自定义" else it.label }
            val autoName = listOf(providerLabel, f.modelName)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
                .ifBlank { "配置 ${configs.value.size + 1}" }
            val config = f.copy(
                id = f.id.ifBlank { UUID.randomUUID().toString() },
                name = f.name.ifBlank { autoName }
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

    /** 测试连接:先取模型列表,再按需退回最小对话请求,结果按类型回显 */
    fun testConnection() {
        if (_testing.value) return
        viewModelScope.launch {
            val config = _form.value
            if (!config.isComplete) {
                _testOutcome.value = TestOutcome(
                    false, TestKind.INVALID, "请先填写接口 URL、API Key 和模型名称"
                )
                return@launch
            }
            _testing.value = true
            val outcome = providerApiClient.testConnection(config)
            _testing.value = false
            if (outcome.ok && outcome.models.isNotEmpty()) {
                _models.value = outcome.models
                _modelsFromApi.value = true
            }
            _testOutcome.value = outcome
        }
    }

    // ---------- 备份导入导出 ----------

    private val _exportIntent = MutableStateFlow<Intent?>(null)
    val exportIntent: StateFlow<Intent?> = _exportIntent.asStateFlow()

    private val _backupResult = MutableStateFlow<Pair<Boolean, String>?>(null)
    val backupResult: StateFlow<Pair<Boolean, String>?> = _backupResult.asStateFlow()

    private fun backupDir(app: Application) = File(app.cacheDir, "backup")

    private fun clearBackupCache(app: Application) {
        runCatching { backupDir(app).listFiles()?.forEach { it.delete() } }
    }

    init {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) { clearBackupCache(app) }
    }

    fun exportConfigs() {
        viewModelScope.launch {
            val app = getApplication<Application>()
            val file = withContext(Dispatchers.IO) {
                runCatching {
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
