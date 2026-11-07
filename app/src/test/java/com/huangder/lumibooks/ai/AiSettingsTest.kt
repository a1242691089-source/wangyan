package com.huangder.lumibooks.ai

import com.huangder.lumibooks.translation.TranslationAuth
import com.huangder.lumibooks.translation.TranslationProvider
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiSettingsTest {
    @Test fun jsonRoundTripPreservesAllFields() {
        val settings = AiSettings(enabled = true, provider = TranslationProvider.CUSTOM,
            baseUrl = "https://example.com/v1/", auth = TranslationAuth.API_KEY,
            model = " model-x ", allowHttp = true, credentialVersion = "v1")
        assertEquals(settings, AiSettings.fromJson(settings.toJson()))
    }

    @Test fun fromJsonFallsBackToDefaultsOnGarbage() {
        val defaults = AiSettings()
        assertEquals(defaults, AiSettings.fromJson(null))
        assertEquals(defaults, AiSettings.fromJson("not json"))
        assertEquals(defaults, AiSettings.fromJson("{}"))
    }

    @Test fun normalizedTrimsAndFollowsProviderAuth() {
        val custom = AiSettings(provider = TranslationProvider.CUSTOM,
            baseUrl = " https://example.com/v1/ ", auth = TranslationAuth.API_KEY,
            model = " m ").normalized()
        assertEquals("https://example.com/v1", custom.baseUrl)
        assertEquals("m", custom.model)
        assertEquals(TranslationAuth.API_KEY, custom.auth)

        val fixed = AiSettings(provider = TranslationProvider.OPENAI,
            baseUrl = " https://api.openai.com/v1 ", auth = TranslationAuth.API_KEY).normalized()
        assertEquals(TranslationProvider.OPENAI.auth, fixed.auth)
    }

    @Test fun credentialScopeSeparatesAiFromTranslation() {
        val settings = AiSettings(provider = TranslationProvider.DEEPSEEK).normalized()
        assertEquals("BEARER|https://api.deepseek.com/v1", settings.credentialScope)
    }
}
