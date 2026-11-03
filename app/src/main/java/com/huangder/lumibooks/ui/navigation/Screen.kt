package com.huangder.lumibooks.ui.navigation

sealed class Screen(val route: String) {
    object Welcome : Screen("welcome")
    object Home : Screen("home")

    /** 全局阅读标记（跨书的时间线），五栏中的「笔记」。 */
    object Annotations : Screen("annotations")

    /** 知识图谱，占位阶段只有空状态。 */
    object Knowledge : Screen("knowledge")

    /** 技能运行时，占位阶段只有空状态。 */
    object Skills : Screen("skills")

    /** 个人中心：主题预设与二级入口。 */
    object Profile : Screen("profile")

    /** 书库管理（文件夹/CoverFlow/分类），从书架页进入。 */
    object Bookshelf : Screen("bookshelf")

    /** 阅读统计，从「我的」进入。 */
    object Statistics : Screen("statistics")
    object Reader : Screen("reader/{bookId}") {
        fun createRoute(bookId: String) = "reader/$bookId"
    }
    object Settings : Screen("settings")
    object Notes : Screen("notes/{bookId}") {
        fun createRoute(bookId: String) = "notes/$bookId"
    }
}
