package com.huangder.lumibooks.ui.reader

import androidx.compose.runtime.Immutable
import com.huangder.lumibooks.domain.model.CustomFontPreset
import com.huangder.lumibooks.domain.model.LongPressSelectionRange
import com.huangder.lumibooks.domain.model.ReaderEdgeTapMode
import com.huangder.lumibooks.domain.model.ReaderImageAdjustments
import com.huangder.lumibooks.domain.model.ReaderTextAlignment

/**
 * [EpubWebViewReader] 的全部渲染配置，从 32 个独立参数收成一个值对象。
 *
 * 注意与 [NativeEpubRenderConfig] 区分：那个是原生分页渲染器的像素级配置，
 * 这个是 Compose 侧阅读器的完整渲染口径（字体、配色、背景、版式、翻页）。
 *
 * 契约：
 * - 字段一律 `val`，不允许改成 `var` / `MutableList` / 可变 state；
 * - [customFonts] 必须是不可变快照（禁止调用方在传入后原地修改），
 *   否则 `equals` 仍为真而内容已变，Compose 会错误跳过重组。
 *
 * 标 `@Immutable` 是必须的，不是风格选择：Kotlin 2.3 默认开启 strong skipping，
 * 不稳定参数按 `===` 比较，而调用方每次重组都会内联新建这个对象；
 * 一旦被判为不稳定，4000 行的 [EpubWebViewReader] 将永不跳过重组。
 */
@Immutable
internal data class EpubRenderConfig(
    // ---- 字体与文字排版 ----
    val fontSizeSp: Float,
    val fontType: String,
    val fontFilePath: String?,
    val customFonts: List<CustomFontPreset> = emptyList(),
    val bodyFontWeight: Int = 400,
    val letterSpacingDp: Float = 0f,
    val textSpacing: EpubTextSpacing = EpubTextSpacing(),
    val textAlignment: ReaderTextAlignment = ReaderTextAlignment.NATURAL,
    val bionicReadingEnabled: Boolean = false,
    val chineseMode: String = "original",
    // ---- 配色与背景 ----
    val textColorOverride: Int?,
    val theme: String,
    val coverBackgroundColor: Int?,
    /**
     * Base color under the pages when the reader uses its own background
     * (custom preset or custom image). Null keeps the built-in theme colors so
     * day/night/sepia/green stay pixel-identical to before.
     */
    val readerBackgroundColorOverride: Int? = null,
    val readerBackgroundImagePath: String? = null,
    val readerBackgroundImageOpacity: Float = 1f,
    val readerBackgroundImageBlurDp: Float = 0f,
    /** Auto-derived text color, applied only where the reader background shows. */
    val autoTextColor: Int? = null,
    /** 整页图片按屏幕比例裁切铺满（默认等比留白）。 */
    val imagePageCrop: Boolean = false,
    /** 「原排版」套装：阅读器不参与配色，底色与文字颜色完全交给书籍自己。 */
    val publisherPaintOnly: Boolean = false,
    val preservePublisherBackground: Boolean = true,
    /** Per-book brightness/contrast/sharpen, already passed through `forDisplay(eInk)`. */
    val imageAdjustments: ReaderImageAdjustments = ReaderImageAdjustments(),
    // ---- 版式与翻页 ----
    val marginTopDp: Float = 0f,
    val marginRightDp: Float = 0f,
    val marginBottomDp: Float = 0f,
    val marginLeftDp: Float = 0f,
    val continuousScroll: Boolean = false,
    val pageTransition: String = "slide",
    val pageTransitionDurationMs: Int = 260,
    val edgeTapMode: ReaderEdgeTapMode = ReaderEdgeTapMode.LEFT_PREVIOUS_RIGHT_NEXT,
    val longPressSelectionRange: LongPressSelectionRange = LongPressSelectionRange.DEFAULT,
    // ---- 渲染引擎开关 ----
    val nativeCssEnabled: Boolean = false
)

/** 原生分页只实现了这三种翻页动画；不放 function 里是为了避免每次重组都新建集合。 */
private val supportedNativeTransitions = setOf("slide", "curl", "none")

/**
 * 原生分页渲染（`NativeEpubReader`）被哪些条件挡下。空列表 = 可用。
 *
 * 这份列表同时是**判定**与**日志**的唯一来源：以前布尔判定写在 Composable 里、
 * 原因文案另手写 11 条 `if`，两份清单必然会漂移。返回顺序即日志顺序。
 *
 * [hasNativeSession] / [bookFormat] / [mediaOnlyPage] 不属于渲染配置，
 * 但它们是「是否支持」判定的一部分，所以作为参数传入而不是留给调用方 `&&` 拼接。
 */
internal fun EpubRenderConfig.nativeSettingsBlockers(
    hasNativeSession: Boolean,
    bookFormat: String,
    mediaOnlyPage: Boolean?
): List<String> = buildList {
    if (!nativeCssEnabled) add("experiment disabled")
    if (!hasNativeSession || bookFormat != "EPUB") add("format $bookFormat")
    // null（首帧 produceState 未回）与 true 一样会挡下原生引擎，但前者不等于确定是整页图。
    if (mediaOnlyPage != false) add("media-only page")
    if (continuousScroll) add("continuous scroll")
    if (pageTransition !in supportedNativeTransitions) add("transition $pageTransition")
    if (readerBackgroundImagePath != null) add("background image")
    if (bionicReadingEnabled) add("bionic reading")
    if (chineseMode != "original") add("Chinese conversion $chineseMode")
    if (textAlignment != ReaderTextAlignment.NATURAL) add("alignment $textAlignment")
    if (letterSpacingDp != 0f) add("letter spacing")
    if (imagePageCrop) add("image crop")
    if (!imageAdjustments.isNeutral) add("image adjustments")
}
