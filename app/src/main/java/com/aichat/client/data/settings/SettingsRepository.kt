package com.aichat.client.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aichat.client.data.security.Crypto
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** 深色模式三态 */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** 密文前缀标记:以此区分新密文与老版本遗留的明文 */
private const val ENC_PREFIX = "enc:"

private fun encryptKey(plain: String): String =
    if (plain.isBlank()) "" else ENC_PREFIX + Crypto.encrypt(plain)

private fun decryptKey(stored: String): String = when {
    stored.startsWith(ENC_PREFIX) -> Crypto.decrypt(stored.removePrefix(ENC_PREFIX)) ?: ""
    else -> stored   // 老版本明文,平滑兼容,下次保存时自动加密
}

// DataStore:轻量化键值存储,存模型配置(多套配置以 JSON 数组保存)
private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val gson = Gson()
    private val KEY_CONFIGS = stringPreferencesKey("model_configs")
    private val KEY_ACTIVE_ID = stringPreferencesKey("active_config_id")
    private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    private val KEY_ACCENT = intPreferencesKey("accent_color")

    // ---------- 主题设置 ----------

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[KEY_THEME_MODE]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    /** 主题色 ARGB(默认靛蓝 0xFF3F51B5) */
    val accentColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[KEY_ACCENT] ?: 0xFF3F51B5.toInt()
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[KEY_THEME_MODE] = when (mode) {
                ThemeMode.SYSTEM -> "system"
                ThemeMode.LIGHT -> "light"
                ThemeMode.DARK -> "dark"
            }
        }
    }

    suspend fun setAccentColor(argb: Int) {
        context.dataStore.edit { prefs -> prefs[KEY_ACCENT] = argb }
    }

    // ---------- 解析(手写反序列化:老版本保存的数据缺新字段时取默认值) ----------
    // decryptKeys=false 返回存储原样(内部改写时用,避免密文被明文回写)
    // decryptKeys=true  返回可用配置(对外流)

    private fun parseConfigs(json: String?, decryptKeys: Boolean): List<ModelConfig> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val type = object : TypeToken<List<JsonObject>>() {}.type
            val arr: List<JsonObject> = gson.fromJson(json, type)
            arr.map { parseConfig(it, decryptKeys) }
        }.getOrDefault(emptyList())
    }

    private fun parseConfig(o: JsonObject, decryptKeys: Boolean): ModelConfig {
        val storedKey = o.str("apiKey") ?: ""
        return ModelConfig(
            id = o.str("id").orEmpty(),
            name = o.str("name") ?: "默认配置",
            baseUrl = o.str("baseUrl") ?: "",
            apiKey = if (decryptKeys) decryptKey(storedKey) else storedKey,
            modelName = o.str("modelName") ?: "",
            temperature = o.num("temperature") ?: 0.7f,
            topP = o.num("topP") ?: 1.0f,
            maxTokens = o.int("maxTokens") ?: 2048,
            timeoutSeconds = o.int("timeoutSeconds") ?: 60,
            contextRounds = o.int("contextRounds") ?: 10,
            streamEnabled = o.bool("streamEnabled") ?: true,
            customBody = o.str("customBody") ?: ""
        ).sanitized()
    }

    private fun JsonObject.str(key: String): String? =
        get(key)?.takeIf { !it.isJsonNull }?.asString

    private fun JsonObject.num(key: String): Float? =
        get(key)?.takeIf { !it.isJsonNull }?.asFloat

    private fun JsonObject.int(key: String): Int? =
        get(key)?.takeIf { !it.isJsonNull }?.asInt

    private fun JsonObject.bool(key: String): Boolean? =
        get(key)?.takeIf { !it.isJsonNull }?.asBoolean

    // ---------- 流 ----------

    /** 全部配置列表(API Key 已解密) */
    val allConfigs: Flow<List<ModelConfig>> = context.dataStore.data.map { prefs ->
        parseConfigs(prefs[KEY_CONFIGS], decryptKeys = true)
    }

    /** 当前生效配置(未选中时回退到第一套;API Key 已解密) */
    val activeConfig: Flow<ModelConfig?> = context.dataStore.data.map { prefs ->
        val list = parseConfigs(prefs[KEY_CONFIGS], decryptKeys = true)
        val activeId = prefs[KEY_ACTIVE_ID]
        list.firstOrNull { it.id == activeId } ?: list.firstOrNull()
    }

    // ---------- 操作 ----------

    /** 新增或更新一套配置(自动清洗参数,API Key 加密落盘),并设为当前生效 */
    suspend fun saveConfig(config: ModelConfig) {
        val clean = config.sanitized()
        val stored = clean.copy(apiKey = encryptKey(clean.apiKey))
        context.dataStore.edit { prefs ->
            // 内部改写用存储原样(密文),避免其他配置的密钥被明文回写
            val current = parseConfigs(prefs[KEY_CONFIGS], decryptKeys = false)
            val list = if (current.any { it.id == stored.id }) {
                current.map { if (it.id == stored.id) stored else it }
            } else {
                current + stored
            }
            prefs[KEY_CONFIGS] = gson.toJson(list)
            prefs[KEY_ACTIVE_ID] = stored.id
        }
    }

    suspend fun deleteConfig(id: String) {
        context.dataStore.edit { prefs ->
            val list = parseConfigs(prefs[KEY_CONFIGS], decryptKeys = false).filterNot { it.id == id }
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
