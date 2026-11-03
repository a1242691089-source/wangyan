package com.huangder.lumibooks.ui.splash

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.AppIconStyle
import com.huangder.lumibooks.util.diagnostics.startupTraceModifier

// 与系统开屏（Android 12+ 的 windowSplashScreenAnimatedIcon）和窗口背景层共用同一几何：
// 288dp 画布上的「忘」标记（108dp 视口内 ink 48dp → 渲染为 128dp）。
// 系统开屏、窗口背景、Compose 开屏三层像素级同构，冷启动只出现一次「忘」。
private val SplashMarkSize = 288.dp

@Composable
fun SplashScreen(isDark: Boolean, iconStyle: String) {
    // 三套图标统一用同一个标记：日夜差异由 colorResource(launch_background)
    // 与 drawable-night/splash_mark 的资源限定符承担，不在此分支。
    // iconStyle 保留在签名里：MainActivity 的调用点不动，后续若要按图标套做
    // 差异化开屏（如夜读专属配色）从这里扩展。
    AppIconStyle.fromStoredValue(iconStyle)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(startupTraceModifier("app_splash"))
            .background(colorResource(R.color.launch_background)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(R.drawable.splash_mark),
            contentDescription = null,
            modifier = Modifier.size(SplashMarkSize),
            contentScale = ContentScale.Fit
        )
    }
}
