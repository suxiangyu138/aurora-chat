package com.aichat.client.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.JsonObject
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

    // ---------- 解析(手写反序列化:老版本保存的数据缺新字段时取默认值) ----------

    private fun parseConfigs(json: String?): List<ModelConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<JsonObject>>() {}.type
            val arr: List<JsonObject> = gson.fromJson(json, type)
            arr.map { parseConfig(it) }
        }.getOrDefault(emptyList())
    }

    private fun parseConfig(o: JsonObject): ModelConfig = ModelConfig(
        id = o.str("id").orEmpty(),
        name = o.str("name") ?: "默认配置",
        baseUrl = o.str("baseUrl") ?: "",
        apiKey = o.str("apiKey") ?: "",
        modelName = o.str("modelName") ?: "",
        temperature = o.num("temperature") ?: 0.7f,
        topP = o.num("topP") ?: 1.0f,
        maxTokens = o.int("maxTokens") ?: 2048,
        timeoutSeconds = o.int("timeoutSeconds") ?: 60,
        contextRounds = o.int("contextRounds") ?: 10,
        streamEnabled = o.bool("streamEnabled") ?: true,
        customBody = o.str("customBody") ?: ""
    ).sanitized()

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.num(key: String): Float? =
        get(key)?.takeIf { !it.isJsonNull }?.asFloat

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull }?.asInt

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean

    // ---------- 流 ----------

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

    // ---------- 操作 ----------

    /** 新增或更新一套配置(自动清洗参数),并设为当前生效 */
    suspend fun saveConfig(config: ModelConfig) {
        val clean = config.sanitized()
        context.dataStore.edit { prefs ->
            val current = parseConfigs(prefs[KEY_CONFIGS])
            val list = if (current.any { it.id == clean.id }) {
                current.map { if (it.id == clean.id) clean else it }
            } else {
                current + clean
            }
            prefs[KEY_CONFIGS] = gson.toJson(list)
            prefs[KEY_ACTIVE_ID] = clean.id
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
