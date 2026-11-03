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
 * 内置预设配色。首个为品牌默认色，与 [DEFAULT_APP_ACCENT_HEX] 保持一致。
 *
 * 每个 hex 都实测过「白字 onPrimary」对比度 ≥ 4.5:1（WCAG AA），由
 * `AppAccentColorTest` 锁定：墨绿 6.78 / 沙青 7.00 / 绀青 8.92 / 蟹青 5.05 /
 * 赭石 5.70 / 鹿皮棕 5.41 / 霁青 4.89 / 青瓷 4.90。
 *
 * 传统釉色本身偏亮（霁青 #63BBD0、青瓷 #B1C5B4、赭石 #C37E5F、鹿皮棕 #BB9755、
 * 蟹青 #7A988E 配白字只有 1.75–3.25:1），因此这里统一存压深后的交互级色值，
 * 原始釉色仅作设计参考，不进代码，避免 onPrimary 文字低于 AA。
 */
val APP_ACCENT_PRESETS: List<AppAccentPreset> = listOf(
    AppAccentPreset("ink_green", R.string.accent_preset_ink_green, DEFAULT_APP_ACCENT_HEX),
    AppAccentPreset("sand_blue", R.string.accent_preset_sand_blue, "#2B5E7D"),
    AppAccentPreset("navy", R.string.accent_preset_navy, "#3F486C"),
    AppAccentPreset("crab_green", R.string.accent_preset_crab_green, "#587568"),
    AppAccentPreset("ochre", R.string.accent_preset_ochre, "#965639"),
    AppAccentPreset("deer_brown", R.string.accent_preset_deer_brown, "#84652D"),
    AppAccentPreset("mist_blue", R.string.accent_preset_mist_blue, "#2E7A8F"),
    AppAccentPreset("celadon", R.string.accent_preset_celadon, "#5C7767")
)
