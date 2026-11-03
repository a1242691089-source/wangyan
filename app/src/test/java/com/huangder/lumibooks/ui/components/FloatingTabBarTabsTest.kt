package com.huangder.lumibooks.ui.components

import com.huangder.lumibooks.ui.navigation.mainTabRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 守卫：底部栏的 Tab 数量必须与 pager 页数一致。
 *
 * 底部栏的点击下标会当作 pager 页下标使用（`selectTab(index)`），
 * 两个列表一旦不同步，点最后一栏就会索引越界崩溃，而且是只在真机点按时才暴露。
 * 这里把它变成编译后立刻失败的断言。
 */
class FloatingTabBarTabsTest {

    @Test
    fun tabCountMatchesPagerPageCount() {
        assertEquals(
            "FloatingTabBar.tabs 与 mainTabRoutes 数量必须一致",
            mainTabRoutes.size,
            tabs.size
        )
    }
}
