package com.huangder.lumibooks.ui.settings

import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.appAccentContentArgb
import com.huangder.lumibooks.domain.model.contrastRatio
import com.huangder.lumibooks.domain.model.parseAppAccentArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 锁死预设色的可读性下限。
 *
 * 预设色直接作为 primary 使用，按钮/选中态上的文字颜色由
 * [appAccentContentArgb] 按亮度阈值自动选黑或白。传统釉色（霁青、青瓷、赭石、
 * 鹿皮棕、蟹青）本身偏亮，若直接拿来当预设，白字对比度会掉到 1.75–3.25，
 * 视觉上"能看"，无障碍上不达标。这里用测试把这条约束固化住。
 */
class AppAccentPresetsTest {

    @Test
    fun everyPresetKeepsPrimaryContentAboveAa() {
        APP_ACCENT_PRESETS.forEach { preset ->
            val accent = parseAppAccentArgb(preset.accentHex)
            val ratio = contrastRatio(accent, appAccentContentArgb(accent))
            assertTrue(
                "${preset.id} (${preset.accentHex}) 的 onPrimary 对比度只有 $ratio，低于 AA 4.5",
                ratio >= 4.5
            )
        }
    }

    @Test
    fun firstPresetIsTheDefaultAccent() {
        assertEquals(DEFAULT_APP_ACCENT_HEX, APP_ACCENT_PRESETS.first().accentHex)
    }

    @Test
    fun presetIdsAreUnique() {
        assertEquals(
            APP_ACCENT_PRESETS.size,
            APP_ACCENT_PRESETS.map { it.id }.toSet().size
        )
    }
}
