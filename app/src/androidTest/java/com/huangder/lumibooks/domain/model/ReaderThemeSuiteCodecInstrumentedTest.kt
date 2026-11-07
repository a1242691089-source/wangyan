package com.huangder.lumibooks.domain.model

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderThemeSuiteCodecInstrumentedTest {
    @Test
    fun codec_roundTripsBuiltInAndCustomSuites() {
        val custom = ReaderThemeSuites.newCustom("focus", "Focus").copy(
            settings = ReaderThemeSettings(
                backgroundSelection = "custom:paper",
                textColor = 0xFF223344.toInt(),
                fontSize = 20f,
                letterSpacing = 4f,
                textAlignment = ReaderTextAlignment.CENTER
            )
        )
        val source = listOf(custom) + ReaderThemeSuites.defaults()

        val decoded = ReaderThemeSuites.normalized(
            ReaderThemeSuiteCodec.decode(ReaderThemeSuiteCodec.encode(source))
        )

        // codec 真实契约：encode 内部先 normalized；decode 把缺失的 light/dark
        // 回填为 settings（冗余副本），故整表 equals 不可行，按身份+设置逐套件比对
        assertEquals(
            ReaderThemeSuites.normalized(source).map { it.copy(lightSettings = null, darkSettings = null) },
            decoded.map { it.copy(lightSettings = null, darkSettings = null) }
        )
        // 文档化回填行为：源侧为 null 的 light/dark，往返后等于 settings
        ReaderThemeSuites.normalized(source).zip(decoded).forEach { (s, d) ->
            if (s.lightSettings == null) assertEquals(s.settings, d.lightSettings)
            if (s.darkSettings == null) assertEquals(s.settings, d.darkSettings)
        }
    }

    @Test
    fun codec_invalidJsonFallsBackWithoutCrashing() {
        assertTrue(ReaderThemeSuiteCodec.decode("not-json").isEmpty())
    }

    @Test
    fun codec_missingTextAlignmentDefaultsToNatural() {
        val decoded = ReaderThemeSuiteCodec.decode(
            """[{"id":"legacy","name":"Legacy","settings":{"background":"day"}}]"""
        )

        assertEquals(ReaderTextAlignment.NATURAL, decoded.single().settings.textAlignment)
    }
}
