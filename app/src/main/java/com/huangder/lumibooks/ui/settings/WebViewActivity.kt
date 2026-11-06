package com.huangder.lumibooks.ui.settings
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.icons.AppIcons

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.huangder.lumibooks.R
import com.huangder.lumibooks.data.local.DataStoreManager
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import com.huangder.lumibooks.ui.theme.EBookReaderTheme
import com.huangder.lumibooks.ui.theme.rememberLiquidGlassCapability
import com.huangder.lumibooks.ui.theme.effectiveAppTheme
import com.huangder.lumibooks.ui.theme.fangSongFamily
import com.huangder.lumibooks.ui.theme.resolveAppFontFamily
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.util.LaunchThemeController
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * 通用 WebView 页面 — 加载 assets/html/ 下的本地 HTML
 *
 * intent extra:
 *   "title"  — 页面标题
 *   "file"   — assets 文件名（如 "privacy.html"）
 *
 * 法律文本按语言分文件：`privacy.html` 是简体基准，其余 locale 用
 * `privacy-<locale>.html`（见 [resolveLegalAsset]）。正文要与顶栏标题
 * 的语言一致，否则会出现「日文标题 + 简体正文」。
 *
 * 过渡动画继承系统默认。
 */
@AndroidEntryPoint
class WebViewActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(com.huangder.lumibooks.util.LocaleHelper.applyLanguage(newBase))
    }

    @Inject
    lateinit var dataStoreManager: DataStoreManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val title = intent.getStringExtra("title") ?: ""
        val file = resolveLegalAsset(this, intent.getStringExtra("file") ?: "privacy.html")

        val launchTheme = LaunchThemeController.themeSnapshot(this)
        val isSystemDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val isDark = when (launchTheme.darkMode) {
            "dark" -> true
            "light" -> false
            else -> isSystemDark
        }

        setContent {
            val appFontSize by dataStoreManager.appFontSize.collectAsState(initial = launchTheme.appFontSize)
            val capability = rememberLiquidGlassCapability(view = LocalView.current)
            val resolvedAppTheme = effectiveAppTheme(launchTheme.appTheme, capability)
            val menuAnimationStyleValue by dataStoreManager.menuAnimationStyle.collectAsState(initial = launchTheme.menuAnimationStyle)
            val liquidGlassStyle by dataStoreManager.liquidGlassStyle
                .collectAsState(initial = launchTheme.liquidGlassStyle)
            val reduceTransparencyEnabled by dataStoreManager.reduceTransparencyEnabled
                .collectAsState(initial = launchTheme.reduceTransparencyEnabled)
            EBookReaderTheme(
                liquidGlassStyle = liquidGlassStyle,
                reduceTransparencyEnabled = reduceTransparencyEnabled,
                menuAnimationStyle = com.huangder.lumibooks.domain.model.MenuAnimationStyle.fromStoredValue(menuAnimationStyleValue),
                darkTheme = isDark,
                dynamicColor = resolvedAppTheme == "material3",
                appTheme = resolvedAppTheme,
                appAccentColor = launchTheme.appAccentColor,
                liquidGlassTransparency = launchTheme.liquidGlassTransparency,
                liquidGlassHdrHighlightEnabled = launchTheme.liquidGlassHdrHighlightEnabled,
                appFontSize = appFontSize,
                globalFontMode = launchTheme.globalFontMode
            ) {
                com.huangder.lumibooks.ui.components.ConfigurableActivityBack(
                    predictiveBackEnabled = launchTheme.predictiveBackEnabled,
                    onBack = { finish() }
                )
                com.huangder.lumibooks.ui.components.LiquidGlassDialogHost(
                    modifier = Modifier.fillMaxSize()
                ) {
                    WebViewPage(title = title, assetFile = file, isDark = isDark, onBack = { finish() })
                }
            }
        }
    }
}

@Composable
private fun WebViewPage(title: String, assetFile: String, isDark: Boolean, onBack: () -> Unit) {
    val bgColor = if (isDark) 0xFF000000.toInt() else 0xFFFBFBFC.toInt()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 顶栏
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpace.sm, vertical = AppSpace.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LiquidGlassIconButton(
                    imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                    contentDescription = stringResource(R.string.back),
                    onClick = onBack,
                    settingsBackButton = true
                )
                Spacer(Modifier.weight(1f))
                Text(title, fontSize = AppType.Section, fontWeight = FontWeight.Bold, fontFamily = resolveAppFontFamily(fangSongFamily()), color = AppColors.TextPrimary)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(48.dp))
            }

            // WebView
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        setBackgroundColor(bgColor)
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                if (isDark) {
                                    view.evaluateJavascript("""
                                        (function(){
                                            var s=document.createElement('style');
                                            s.textContent=':root{--bg:#000;--card:#1C1C1E;--text:#fff;--text2:#98989D;--line:#38383A}';
                                            document.head.appendChild(s);
                                        })();
                                    """.trimIndent(), null)
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.defaultTextEncodingName = "UTF-8"

                        // Decode the asset ourselves before passing it to WebView. This prevents
                        // stale file:// cache entries or device-specific charset sniffing from
                        // turning CJK policy text into question marks after an app update.
                        runCatching {
                            context.assets
                                .open("html/$assetFile")
                                .bufferedReader(Charsets.UTF_8)
                                .use { it.readText() }
                        }.onSuccess { html ->
                            loadDataWithBaseURL(
                                "file:///android_asset/html/",
                                html,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        }.onFailure {
                            loadUrl("file:///android_asset/html/$assetFile")
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * 法律文本的语言后缀。
 *
 * Activity 已在 `attachBaseContext` 里套用应用语言，`system` 也已被解析成具体
 * locale，所以这里读运行时 configuration，不再二次读 SharedPreferences。
 * 简体（zh-CN）不加后缀：`privacy.html` 本身就是基准文件。
 */
private fun legalAssetSuffix(context: Context): String {
    val locale = context.resources.configuration.locales[0]
    return when (locale.language) {
        "ja" -> "-ja"
        "ko" -> "-ko"
        "en" -> "-en"
        "zh" -> when (locale.country.uppercase(Locale.ROOT)) {
            "TW" -> "-zh-TW"
            "HK" -> "-zh-HK"
            "MO" -> "-zh-MO"
            else -> ""
        }
        // 不认识的系统语言：资源系统同样回退到 values/（简体），正文跟着回退，
        // 宁可给简体也不要出现「日文标题 + 英文正文」这种错配。
        else -> ""
    }
}

/**
 * 把 `privacy.html` 解析成实际要加载的 asset：有对应语言版本就用，没有就回退基准文件。
 *
 * 判定用「能否打开」而不是硬编码清单——新增语言只需丢文件进 assets，少一个文件
 * 只会退化成简体，不会白屏。
 */
private fun resolveLegalAsset(context: Context, file: String): String {
    val suffix = legalAssetSuffix(context)
    if (suffix.isEmpty()) return file
    val localized = file.removeSuffix(".html") + suffix + ".html"
    val exists = runCatching { context.assets.open("html/$localized").close() }.isSuccess
    return if (exists) localized else file
}
