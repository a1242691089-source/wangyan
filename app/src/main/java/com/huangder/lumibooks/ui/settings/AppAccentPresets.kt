package com.huangder.lumibooks.ui.settings

import androidx.annotation.StringRes
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX

/**
 * 一套预设配色。
 *
 * 只封装主色：深色模式下所需的提亮版本由
 * [com.huangder.lumibooks.domain.model.deriveDarkAppAccentArgb] 自动派生（保证深色背景对比度 ≥ 7.0），
 * 因此新增预设不需要维护第二份深色色值，也不会出现深色下对比度不足的问题。
 *
 * 预设只是"写哪个 hex 到 app_accent_color"的快捷方式，不引入新的存储键，
 * 以免与用户已有的自定义主色产生默认值冲突。
 */
data class AppAccentPreset(
    val id: String,
    @StringRes val labelRes: Int,
    val accentHex: String
)

/**
 * 内置预设配色。首个为上游默认色，保证不改变现有用户的观感基线。
 */
val APP_ACCENT_PRESETS: List<AppAccentPreset> = listOf(
    AppAccentPreset("coral", R.string.accent_preset_coral, DEFAULT_APP_ACCENT_HEX),
    AppAccentPreset("indigo", R.string.accent_preset_indigo, "#4F5BD5"),
    AppAccentPreset("teal", R.string.accent_preset_teal, "#0E7C7B"),
    AppAccentPreset("amber", R.string.accent_preset_amber, "#B87333"),
    AppAccentPreset("violet", R.string.accent_preset_violet, "#7C4DFF")
)
