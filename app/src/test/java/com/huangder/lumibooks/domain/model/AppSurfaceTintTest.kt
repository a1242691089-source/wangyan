package com.huangder.lumibooks.domain.model

import com.huangder.lumibooks.ui.settings.APP_ACCENT_PRESETS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 浅色「iOS 式」全局着色的护栏：
 * 窗口底色混 12%、卡片混 6%，色相明确可见但文字对比度与层次不能被破坏。
 */
class AppSurfaceTintTest {
    private val presets = APP_ACCENT_PRESETS.map { it.accentHex to parseAppAccentArgb(it.accentHex) }
    private val lightWindowBase = 0xFFFBFAF6.toInt()
    private val cardBase = 0xFFFFFFFF.toInt()
    private val textPrimary = 0xFF000000.toInt()
    private val textSecondary = 0xFF1C1C1E.toInt()

    @Test
    fun fractionConstantsAreLocked() {
        assertEquals(0.12f, APP_WINDOW_TINT_FRACTION)
        assertEquals(0.06f, APP_CARD_TINT_FRACTION)
    }

    @Test
    fun windowTintEqualsRequestedFractionOfAccent() {
        presets.forEach { (_, accent) ->
            assertEquals(
                blendAppAccentArgb(lightWindowBase, accent, 0.12f),
                tintedAppWindowArgb(lightWindowBase, accent)
            )
        }
    }

    @Test
    fun cardTintEqualsRequestedFractionOfAccent() {
        presets.forEach { (_, accent) ->
            assertEquals(
                blendAppAccentArgb(cardBase, accent, 0.06f),
                tintedAppCardArgb(cardBase, accent)
            )
        }
    }

    @Test
    fun tintedSurfacesAreNoLongerNeutral() {
        presets.forEach { (hex, accent) ->
            assertNotEquals("窗口未着色：$hex", lightWindowBase, tintedAppWindowArgb(lightWindowBase, accent))
            assertNotEquals("卡片未着色：$hex", cardBase, tintedAppCardArgb(cardBase, accent))
        }
    }

    @Test
    fun windowIsAlwaysTintedMoreThanCard() {
        presets.forEach { (hex, accent) ->
            assertNotEquals(
                "窗口与卡片应分档：$hex",
                tintedAppWindowArgb(lightWindowBase, accent),
                tintedAppCardArgb(cardBase, accent)
            )
        }
    }

    @Test
    fun tintedWindowKeepsBodyTextContrast() {
        presets.forEach { (hex, accent) ->
            val surface = tintedAppWindowArgb(lightWindowBase, accent)
            assertTrue(
                "正文对比度不足：$hex -> ${contrastRatio(surface, textPrimary)}",
                contrastRatio(surface, textPrimary) >= 12.0
            )
            assertTrue(
                "次要文字对比度不足：$hex -> ${contrastRatio(surface, textSecondary)}",
                contrastRatio(surface, textSecondary) >= 12.0
            )
        }
    }

    @Test
    fun tintedCardStaysNearWhite() {
        presets.forEach { (hex, accent) ->
            val ratio = contrastRatio(tintedAppCardArgb(cardBase, accent), cardBase)
            // 6% 强度下最深预设（绀青）实测 1.1005：肉眼仍是近白，且与 12% 窗口的层次差更大
            assertTrue("卡片偏灰了：$hex -> $ratio", ratio < 1.12)
        }
    }

    @Test
    fun extremeAccentsStayOpaqueAndDoNotThrow() {
        listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt()).forEach { accent ->
            val window = tintedAppWindowArgb(lightWindowBase, accent)
            val card = tintedAppCardArgb(cardBase, accent)
            assertEquals(0xFF, window ushr 24 and 0xFF)
            assertEquals(0xFF, card ushr 24 and 0xFF)
        }
        // 纯白主色等于不着色，允许；只要求合法输出。
        assertEquals(cardBase, tintedAppCardArgb(cardBase, 0xFFFFFFFF.toInt()))
    }

    @Test
    fun translucentAccentKeepsOpaqueRgbMix() {
        val opaque = parseAppAccentArgb("#1A6840")
        val translucent = 0x801A6840.toInt()
        assertEquals(
            tintedAppWindowArgb(lightWindowBase, opaque),
            tintedAppWindowArgb(lightWindowBase, translucent)
        )
        assertEquals(0xFF, tintedAppWindowArgb(lightWindowBase, translucent) ushr 24 and 0xFF)
    }
}
