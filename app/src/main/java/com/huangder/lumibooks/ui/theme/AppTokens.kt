package com.huangder.lumibooks.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.DEFAULT_APP_ACCENT_HEX
import com.huangder.lumibooks.domain.model.parseAppAccentArgb
import com.huangder.lumibooks.domain.model.tintedAppCardArgb
import com.huangder.lumibooks.domain.model.tintedAppWindowArgb
import com.huangder.lumibooks.domain.model.tintedDarkAppCardArgb
import com.huangder.lumibooks.domain.model.tintedDarkAppWindowArgb
import com.huangder.lumibooks.domain.model.LiquidGlassStyle
import com.huangder.lumibooks.util.DownloadedFonts

/** 全局深色模式状态，由 MainActivity 根据 DataStore 设置注入 */
val LocalIsDarkTheme = staticCompositionLocalOf { false }
val LocalUseMaterial3Theme = staticCompositionLocalOf { false }
val LocalAppTheme = staticCompositionLocalOf { "lumi" }
/** Raw persisted theme id, retained when a theme resolves to liquid glass. */
val LocalAppThemeVariant = staticCompositionLocalOf { "lumi" }
val LocalAppAccentHex = staticCompositionLocalOf { DEFAULT_APP_ACCENT_HEX }
val LocalAppAccentColor = staticCompositionLocalOf { Color(parseAppAccentArgb(DEFAULT_APP_ACCENT_HEX)) }
val LocalOnAppAccentColor = staticCompositionLocalOf { Color.White }
/** Disables decorative blur and optical distortion while preserving glass frosting. */
val LocalReduceTransparency = staticCompositionLocalOf { false }
val LocalLiquidGlassStyle = staticCompositionLocalOf { LiquidGlassStyle.DEFAULT }
val LocalLiquidGlassTransparency = staticCompositionLocalOf { 0.55f }
val LocalLiquidGlassHdrHighlightEnabled = staticCompositionLocalOf { false }
val LocalLiquidGlassCapability = staticCompositionLocalOf {
    LiquidGlassCapability(supported = false, hdrSupported = false)
}
val LocalEInkMode = staticCompositionLocalOf { false }
val LocalMotionEnabled = staticCompositionLocalOf { true }
val LocalCardOutlinesEnabled = staticCompositionLocalOf { false }
val LocalCoverReadingProgressEnabled = staticCompositionLocalOf { false }

/** Adds the optional high-contrast edge used by standard (non-liquid) cards. */
@Composable
fun Modifier.cardOutline(shape: Shape): Modifier {
    if (!LocalCardOutlinesEnabled.current || LocalAppTheme.current == "liquid_glass") return this
    val outlineColor = if (LocalIsDarkTheme.current) Color.White else Color.Black
    return border(width = 0.5.dp, color = outlineColor, shape = shape)
}

enum class MotionPreference {
    STANDARD,
    FAST,
    REDUCED;

    companion object {
        fun fromStoredValue(value: String?): MotionPreference =
            when (value) {
                "fast" -> FAST
                "reduced" -> REDUCED
                else -> STANDARD
            }
    }

    val visualDurationScale: Float
        get() = if (this == FAST) 2f / 3f else 1f

    val motionEnabled: Boolean
        get() = this != REDUCED
}

val LocalMotionPreference = staticCompositionLocalOf { MotionPreference.STANDARD }
val LocalMenuAnimationStyle = staticCompositionLocalOf {
    com.huangder.lumibooks.domain.model.MenuAnimationStyle.LIQUID
}

object GlobalFontMode {
    const val DEFAULT = "default"
    const val SYSTEM = "system"

    fun normalize(value: String): String = if (value == SYSTEM) SYSTEM else DEFAULT
}

val LocalGlobalFontMode = staticCompositionLocalOf { GlobalFontMode.DEFAULT }

// ─── 字体 ───
// 仿宋为按需下载字体（远程配置驱动，见 FontDownloadManager）；未下载时回退系统字体
@Composable
fun fangSongFamily(): FontFamily = DownloadedFonts.familyOrDefault("fangsong")

val KaiTi = FontFamily(Font(R.font.lxgw_wenkai, FontWeight.Normal))

/**
 * 宋体，用于页面级大标题（首页 / 书架 / 我的 / 统计，以及笔记·知识·技能三个占位页）。
 * 段落标题（AppType.Section）与书籍名仍用楷体，保持「封面级装饰 vs 内容级标题」的层次差异。
 * res/font/gen_ryu_min2_light.xml 内已声明 android:ttcIndex="2"（源ノ明朝 Light），
 * 用 Font(resId) 让系统按 font-family XML 解析，不必手动处理 ttc 索引——
 * 与同文件 KaiTi 的写法完全一致，不引入新 API。
 * 已知局限：源ノ明朝是日文骨架，简体常用字覆盖完整，但个别字形偏日式（如「直」「骨」）。
 */
val SongTi = FontFamily(Font(R.font.gen_ryu_min2_light, FontWeight.Normal))

val SansSerif = FontFamily.Default

/**
 * Resolves decorative app typography without affecting the reader's own font setting.
 * The default mode preserves the bundled font; system mode uses the platform font.
 */
@Composable
fun resolveAppFontFamily(defaultFontFamily: FontFamily): FontFamily =
    if (LocalGlobalFontMode.current == GlobalFontMode.SYSTEM) FontFamily.Default else defaultFontFamily

// ─── 颜色（自动适配深色模式）───
object AppColors {
    // 浅色
    // 窗口底色直接复用 Color.kt 的公开常量：同一色值曾在这里重复定义一份，改动易漏。
    // 浅色下窗口与卡片混入用户主色（6% / 3%，见 AppAccentColor），深色与 M3 分支不受影响。
    private val LightWindowBg: Color @Composable
        get() = Color(
            tintedAppWindowArgb(BackgroundLight.toArgb(), LocalAppAccentColor.current.toArgb())
        )
    private val LightCardBg: Color @Composable
        get() = Color(
            tintedAppCardArgb(Color.White.toArgb(), LocalAppAccentColor.current.toArgb())
        )
    private val LightTextPrimary = Color(0xFF000000)
    private val LightTextSecondary = Color(0xFF6E6E73)
    private val LightBgGray = Color(0xFFF2F2F7)
    private val LightDivider = Color(0xFFE5E5EA)
    private val LightDanger = Color(0xFFD92D3A)

    // 深色
    // 深色下窗口与卡片混入提亮派生主色（8% / 5%，见 AppAccentColor）；BgGray/Divider 保持中性层次
    private val DarkWindowBg: Color @Composable
        get() = Color(
            tintedDarkAppWindowArgb(BackgroundDark.toArgb(), LocalAppAccentColor.current.toArgb())
        )
    private val DarkCardBg: Color @Composable
        get() = Color(
            tintedDarkAppCardArgb(SurfaceDark.toArgb(), LocalAppAccentColor.current.toArgb())
        )
    private val DarkTextPrimary = Color(0xFFFFFFFF)
    private val DarkTextSecondary = Color(0xFF98989D)
    private val DarkBgGray = Color(0xFF2C2C2E)
    private val DarkDivider = Color(0xFF38383A)
    // 0xFFD92D3A 压在深色底上对比度不足，提亮到浅红
    private val DarkDanger = Color(0xFFFF7A75)

    val Accent: Color @Composable get() = LocalAppAccentColor.current
    val OnAccent: Color @Composable get() = LocalOnAppAccentColor.current
    val ControlActive: Color @Composable get() = if (LocalEInkMode.current) {
        Color.Black
    } else if (LocalUseMaterial3Theme.current) {
        MaterialTheme.colorScheme.primary
    } else if (LocalIsDarkTheme.current) DarkTextPrimary else LightTextPrimary
    val Scrim: Color @Composable get() = if (LocalUseMaterial3Theme.current) {
        MaterialTheme.colorScheme.scrim
    } else Color.Black
    val Shadow = Color(0x08000000)
    val CardShadow: Color @Composable get() = if (LocalEInkMode.current) {
        Color.Transparent
    } else if (LocalUseMaterial3Theme.current) {
        Color.Black.copy(alpha = 0.18f)
    } else Color(0x06000000)

    // Material 3 uses the system palette; Default and Liquid Glass use the configured accent.
    val WindowBg: Color @Composable get() = if (LocalEInkMode.current) Color.White else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.background else if (LocalIsDarkTheme.current) DarkWindowBg else LightWindowBg
    val PageBg: Color @Composable get() = if (LocalLumiBackgroundPresent.current) Color.Transparent else WindowBg
    val CardBg: Color @Composable get() = if (LocalEInkMode.current) Color.White else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.surfaceContainerLow else if (LocalIsDarkTheme.current) DarkCardBg else LightCardBg
    val TextPrimary: Color @Composable get() = if (LocalEInkMode.current) Color.Black else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.onBackground else if (LocalIsDarkTheme.current) DarkTextPrimary else LightTextPrimary
    val TextSecondary: Color @Composable get() = if (LocalEInkMode.current) Color(0xFF444444) else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.onSurfaceVariant else if (LocalIsDarkTheme.current) DarkTextSecondary else LightTextSecondary
    val BgGray: Color @Composable get() = if (LocalEInkMode.current) Color(0xFFF4F4F4) else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.surfaceVariant else if (LocalIsDarkTheme.current) DarkBgGray else LightBgGray
    val Divider: Color @Composable get() = if (LocalEInkMode.current) Color(0xFFBDBDBD) else if (LocalUseMaterial3Theme.current) MaterialTheme.colorScheme.outlineVariant else if (LocalIsDarkTheme.current) DarkDivider else LightDivider

    /** 危险 / 删除类操作。e-ink 下退化为纯黑，保证墨屏对比度。 */
    val Danger: Color @Composable get() = when {
        LocalEInkMode.current -> Color.Black
        LocalIsDarkTheme.current -> DarkDanger
        else -> LightDanger
    }
}

// ─── 字阶 ───
object AppType {
    val Display = 32.sp
    val Title = 28.sp
    val Section = 20.sp
    val Body = 16.sp
    val BodySmall = 14.sp
    val Caption = 12.sp
    val Huge = 36.sp
}

// ─── 间距 ───
object AppSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

// ─── 圆角 ───
object AppRadius {
    val sm = 8.dp
    // Softer card corners without turning compact controls into pills.
    val md = 14.dp
    val lg = 18.dp
    val xl = 22.dp
    val full = 999.dp
    val capsule = 28.dp
}

// ─── 线宽 ───
object AppStroke {
    /** 发丝线：分隔线默认粗细，物理半像素，靠 18% 文字色形成层次而不是靠加粗。 */
    val Hairline = 0.5.dp
}
