package com.huangder.lumibooks.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

const val DEFAULT_APP_ACCENT_HEX = "#1A6840"

/** 浅色窗口底色混入主色的比例。文字对比度仍 ≥12:1，但整页色相明确可见。 */
const val APP_WINDOW_TINT_FRACTION = 0.12f

/** 浅色卡片底色混入主色的比例。卡片必须比窗口更接近白，否则层次会糊。 */
const val APP_CARD_TINT_FRACTION = 0.06f

/** 深色窗口底色混入主色的比例。纯黑底上白字对比度仍 ≥18:1，整页色相明确可见。 */
const val APP_WINDOW_TINT_DARK_FRACTION = 0.08f

/** 深色卡片底色混入主色的比例。卡片必须仍比窗口亮，保持深色层次。 */
const val APP_CARD_TINT_DARK_FRACTION = 0.05f


private const val DARK_CARD_ARGB = 0xFF1C1C1E.toInt()
private const val DARK_ACCENT_MIN_CONTRAST = 7.0

fun normalizeAppAccentHex(value: String?): String {
    val digits = value?.trim()?.removePrefix("#").orEmpty()
    return if (digits.matches(Regex("^[0-9A-Fa-f]{6}$"))) {
        "#${digits.uppercase()}"
    } else {
        DEFAULT_APP_ACCENT_HEX
    }
}

fun parseAppAccentArgb(value: String?): Int =
    (0xFF000000L or normalizeAppAccentHex(value).drop(1).toLong(16)).toInt()

fun appAccentHex(argb: Int): String = String.format("#%06X", argb and 0xFFFFFF)

fun appAccentContentArgb(accentArgb: Int): Int =
    if (relativeLuminance(accentArgb) > 0.45) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

fun deriveDarkAppAccentArgb(accentArgb: Int): Int {
    val opaqueAccent = accentArgb or 0xFF000000.toInt()
    if (contrastRatio(opaqueAccent, DARK_CARD_ARGB) >= DARK_ACCENT_MIN_CONTRAST) {
        return opaqueAccent
    }

    val hsl = rgbToHsl(opaqueAccent)
    var low = hsl.lightness
    var high = 1f
    repeat(18) {
        val candidateLightness = (low + high) / 2f
        val candidate = hslToArgb(hsl.copy(lightness = candidateLightness))
        if (contrastRatio(candidate, DARK_CARD_ARGB) >= DARK_ACCENT_MIN_CONTRAST) {
            high = candidateLightness
        } else {
            low = candidateLightness
        }
    }
    return hslToArgb(hsl.copy(lightness = high))
}

fun blendAppAccentArgb(backgroundArgb: Int, foregroundArgb: Int, foregroundFraction: Float): Int {
    val amount = foregroundFraction.coerceIn(0f, 1f)
    fun blendChannel(shift: Int): Int {
        val background = backgroundArgb ushr shift and 0xFF
        val foreground = foregroundArgb ushr shift and 0xFF
        return (background + (foreground - background) * amount).roundToInt().coerceIn(0, 255)
    }
    return (0xFF shl 24) or
        (blendChannel(16) shl 16) or
        (blendChannel(8) shl 8) or
        blendChannel(0)
}

/** 浅色窗口底色：在中性底色上混入 [APP_WINDOW_TINT_FRACTION] 主色。 */
fun tintedAppWindowArgb(baseArgb: Int, accentArgb: Int): Int =
    blendAppAccentArgb(baseArgb, accentArgb, APP_WINDOW_TINT_FRACTION)

/** 浅色卡片底色：在近白底色上混入 [APP_CARD_TINT_FRACTION] 主色。 */
fun tintedAppCardArgb(baseArgb: Int, accentArgb: Int): Int =
    blendAppAccentArgb(baseArgb, accentArgb, APP_CARD_TINT_FRACTION)

/**
 * 深色窗口底色：在纯黑底上混入 [APP_WINDOW_TINT_DARK_FRACTION] 主色。
 * [accentArgb] 期望传深色派生提亮色（[deriveDarkAppAccentArgb] 的结果，即深色模式下
 * LocalAppAccentColor 持有的值），与深色装饰混色先例（primaryContainer 24%、
 * Welcome 开始按钮 14%）同源；原始主色混 8% 进纯黑肉眼不可见。
 */
fun tintedDarkAppWindowArgb(baseArgb: Int, accentArgb: Int): Int =
    blendAppAccentArgb(baseArgb, accentArgb, APP_WINDOW_TINT_DARK_FRACTION)

/** 深色卡片底色：在 0xFF1C1C1E 底上混入 [APP_CARD_TINT_DARK_FRACTION] 主色。 */
fun tintedDarkAppCardArgb(baseArgb: Int, accentArgb: Int): Int =
    blendAppAccentArgb(baseArgb, accentArgb, APP_CARD_TINT_DARK_FRACTION)


internal fun contrastRatio(firstArgb: Int, secondArgb: Int): Double {
    val first = relativeLuminance(firstArgb)
    val second = relativeLuminance(secondArgb)
    return (max(first, second) + 0.05) / (min(first, second) + 0.05)
}

private fun relativeLuminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val value = channel / 255.0
        return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
    }
    val red = linear(argb ushr 16 and 0xFF)
    val green = linear(argb ushr 8 and 0xFF)
    val blue = linear(argb and 0xFF)
    return 0.2126 * red + 0.7152 * green + 0.0722 * blue
}

private data class Hsl(val hue: Float, val saturation: Float, val lightness: Float)

private fun rgbToHsl(argb: Int): Hsl {
    val red = (argb ushr 16 and 0xFF) / 255f
    val green = (argb ushr 8 and 0xFF) / 255f
    val blue = (argb and 0xFF) / 255f
    val maximum = max(red, max(green, blue))
    val minimum = min(red, min(green, blue))
    val delta = maximum - minimum
    val lightness = (maximum + minimum) / 2f
    if (delta == 0f) return Hsl(0f, 0f, lightness)

    val saturation = delta / (1f - abs(2f * lightness - 1f))
    val hue = when (maximum) {
        red -> 60f * (((green - blue) / delta) % 6f)
        green -> 60f * (((blue - red) / delta) + 2f)
        else -> 60f * (((red - green) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Hsl(hue, saturation, lightness)
}

private fun hslToArgb(hsl: Hsl): Int {
    val chroma = (1f - abs(2f * hsl.lightness - 1f)) * hsl.saturation
    val hueSection = hsl.hue / 60f
    val secondary = chroma * (1f - abs(hueSection % 2f - 1f))
    val (redPrime, greenPrime, bluePrime) = when {
        hueSection < 1f -> Triple(chroma, secondary, 0f)
        hueSection < 2f -> Triple(secondary, chroma, 0f)
        hueSection < 3f -> Triple(0f, chroma, secondary)
        hueSection < 4f -> Triple(0f, secondary, chroma)
        hueSection < 5f -> Triple(secondary, 0f, chroma)
        else -> Triple(chroma, 0f, secondary)
    }
    val match = hsl.lightness - chroma / 2f
    fun channel(value: Float): Int = ((value + match) * 255f).roundToInt().coerceIn(0, 255)
    return (0xFF shl 24) or
        (channel(redPrime) shl 16) or
        (channel(greenPrime) shl 8) or
        channel(bluePrime)
}
