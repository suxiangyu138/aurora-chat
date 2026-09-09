package com.aichat.client.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore:轻量化键值存储,存模型配置(多套配置以 JSON 数组保存)
private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()
    private val KEY_CONFIGS = stringPreferencesKey("model_configs")
    private val KEY_ACTIVE_ID = stringPreferencesKey("active_config_id")

    private fun parseConfigs(json: String?): List<ModelConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<ModelConfig>>() {}.type
            gson.fromJson<List<ModelConfig>>(json, type)
        }.getOrDefault(emptyList())
    }

    /** 全部配置列表 */
    val allConfigs: Flow<List<ModelConfig>> = context.dataStore.data.map { prefs ->
        parseConfigs(prefs[KEY_CONFIGS])
    }

    /** 当前生效配置(未选中时回退到第一套) */
    val activeConfig: Flow<ModelConfig?> = context.dataStore.data.map { prefs ->
        val list = parseConfigs(prefs[KEY_CONFIGS])
        val activeId = prefs[KEY_ACTIVE_ID]
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    /** 新增或更新一套配置,并设为当前生效 */
    suspend fun saveConfig(config: ModelConfig) {
        context.dataStore.edit { prefs ->
            val current = parseConfigs(prefs[KEY_CONFIGS])
            val list = if (current.any { it.id == config.id }) {
                current.map { if (it.id == config.id) config else it }
            } else {
                current + config
            }
            prefs[KEY_CONFIGS] = gson.toJson(list)
            prefs[KEY_ACTIVE_ID] = config.id
        }
    }

    suspend fun deleteConfig(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseConfigs(prefs[KEY_CONFIGS]).filterNot { it.id == id }
            prefs[KEY_CONFIGS] = gson.toJson(list)
            if (prefs[KEY_ACTIVE_ID] == id) {
                prefs[KEY_ACTIVE_ID] = list.firstOrNull()?.id.orEmpty()
            }
        }
    }

    suspend fun setActive(id: String) {
        context.dataStore.edit { prefs -> prefs[KEY_ACTIVE_ID] = id }
    }

    suspend fun getActiveConfig(): ModelConfig? = activeConfig.first()
}
