package com.huangder.lumibooks.ai

import android.content.Context
import android.content.Intent
import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationProvider
import org.json.JSONObject

fun openAiSettings(context: Context) {
    context.startActivity(Intent(context,
        com.huangder.lumibooks.ui.settings.DetailActivity::class.java).putExtra("category", "ai_settings"))
}

/**
 * 通用 AI 服务设置（V0.5a）。Provider 与鉴权方式与 AI 翻译共用同一组枚举：
 * 两者都是 OpenAI 兼容的 chat/completions 服务，可选服务商完全一致。
 * 与翻译设置相互独立——各自存储、各自的密钥仓，互不覆盖。
 */
data class AiSettings(
    val enabled: Boolean = false,
    val provider: TranslationProvider = TranslationProvider.DEEPSEEK,
    val baseUrl: String = TranslationProvider.DEEPSEEK.baseUrl,
    val auth: TranslationAuth = TranslationAuth.BEARER,
    val model: String = "",
    val allowHttp: Boolean = false,
    // Changes only when credentials are replaced, so active requests can be invalidated.
    val credentialVersion: String = ""
) {
    fun normalized() = copy(baseUrl = baseUrl.trim().trimEnd('/'), model = model.trim(),
        auth = if (provider == TranslationProvider.CUSTOM) auth else provider.auth)

    val credentialScope: String get() = "${normalized().auth}|${normalized().baseUrl}"

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled); put("provider", provider.name); put("baseUrl", baseUrl)
        put("auth", auth.name); put("model", model); put("allowHttp", allowHttp)
        put("credentialVersion", credentialVersion)
    }.toString()

    companion object {
        fun fromJson(raw: String?): AiSettings = runCatching {
            val json = JSONObject(raw ?: return AiSettings())
            val provider = TranslationProvider.valueOf(json.optString("provider", "DEEPSEEK"))
            AiSettings(json.optBoolean("enabled"), provider,
                json.optString("baseUrl", provider.baseUrl),
                TranslationAuth.valueOf(json.optString("auth", provider.auth.name)),
                json.optString("model"), json.optBoolean("allowHttp"),
                json.optString("credentialVersion"))
        }.getOrDefault(AiSettings())
    }
}

data class AiConfiguration(val settings: AiSettings, val hasToken: Boolean) {
    val enabled: Boolean get() = settings.enabled && hasToken
}

/** 一条对话消息。systemPrompt 独立传参时不必塞进列表。 */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = AiMessage("system", content)
        fun user(content: String) = AiMessage("user", content)
        fun assistant(content: String) = AiMessage("assistant", content)
    }
}
