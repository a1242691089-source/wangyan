package com.huangder.lumibooks.ui.bookshelf

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

// 自真机迁移（原 BookDetailsLayoutInstrumentedTest 的 Compose 布局边界断言；
// 纯函数判定已由 BookDetailsLayoutTest 覆盖）：规避 Android 16 真机
// androidx.test EmptyActivity 窗口舞步的随机楔死竞态。
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "zh-rCN-w369dp-h822dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BookDetailRowComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun longUnbrokenFileNameMovesBelowLabel() {
        val label = "文件名"
        val value = "very_long_unbroken_filename_abcdefghijklmnopqrstuvwxyz0123456789.epub"
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                DetailRow(label, value, clickable = false)
            }
        }

        val labelBounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
        assertTrue(valueBounds.top >= labelBounds.bottom)
        assertTrue(valueBounds.height > labelBounds.height)
    }

    @Test
    fun shortFieldRemainsOnSameLine() {
        val label = "格式"
        val value = "EPUB"
        composeRule.setContent {
            Box(Modifier.width(320.dp)) {
                DetailRow(label, value, clickable = false)
            }
        }

        val labelBounds = composeRule.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        val valueBounds = composeRule.onNodeWithText(value).fetchSemanticsNode().boundsInRoot
        assertTrue(valueBounds.top < labelBounds.bottom)
        assertTrue(labelBounds.top < valueBounds.bottom)
    }
}
