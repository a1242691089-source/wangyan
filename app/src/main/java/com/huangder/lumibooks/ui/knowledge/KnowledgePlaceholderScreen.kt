package com.huangder.lumibooks.ui.knowledge

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ui.components.FeaturePlaceholder
import com.huangder.lumibooks.ui.icons.AppIcons

/**
 * 知识页（占位）。
 *
 * 这一栏在产品规划里承载「节点—关系—证据」三张表与图谱视图，
 * 现在只落地导航骨架，因此先给空状态，不建表、不读数据库。
 */
@Composable
fun KnowledgePlaceholderScreen(modifier: Modifier = Modifier) {
    FeaturePlaceholder(
        title = stringResource(R.string.nav_knowledge),
        icon = AppIcons.KnowledgeTab.resolve(false),
        headline = stringResource(R.string.knowledge_empty_title),
        body = stringResource(R.string.knowledge_empty_body),
        footnote = stringResource(R.string.knowledge_empty_footnote),
        modifier = modifier
    )
}
