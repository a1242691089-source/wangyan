package com.huangder.lumibooks.ui.theme

import androidx.compose.ui.graphics.Color

// 主色调 - 忘言墨绿
// 仅作为 Material3 分支里 accent 未派生到时的兜底值；用户选了中国传统色预设后，
// Theme.kt 会用 accent 覆盖 primary，这两个常量不参与。
val PrimaryLight = Color(0xFF1A6840)
val PrimaryDark = Color(0xFF3E9268)

// 次要色
val SecondaryLight = Color(0xFF5856D6)
val SecondaryDark = Color(0xFF5E5CE6)

// 背景色
val BackgroundLight = Color(0xFFFBFAF6)
val BackgroundDark = Color(0xFF000000)

// 表面色
val SurfaceLight = Color.White
val SurfaceDark = Color(0xFF1C1C1E)

// 文字颜色
val OnBackgroundLight = Color(0xFF1C1C1E)
val OnBackgroundDark = Color(0xFFFFFFFF)

val OnSurfaceLight = Color(0xFF3C3C43)
val OnSurfaceDark = Color(0xFFEBEBF5)

// 错误色
val ErrorLight = Color(0xFFFF3B30)
val ErrorDark = Color(0xFFFF453A)

// 阅读主题颜色
val ReaderDayBackground = Color(0xFFFFFFFF)
val ReaderDayTextColor = Color(0xFF1C1C1E)

val ReaderNightBackground = Color(0xFF1C1C1E)
val ReaderNightTextColor = Color(0xFFEBEBF5)

val ReaderSepiaBackground = Color(0xFFF5E6D3)
val ReaderSepiaTextColor = Color(0xFF3E2723)

val ReaderGreenBackground = Color(0xFFE8F5E9)
val ReaderGreenTextColor = Color(0xFF1B5E20)

// 图表颜色
val ChartPrimary = Color(0xFF007AFF)
val ChartSecondary = Color(0xFF5856D6)
val ChartTertiary = Color(0xFF34C759)
