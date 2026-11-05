package com.huangder.lumibooks.ui.knowledge

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.KnowledgeCard
import com.huangder.lumibooks.ui.components.AnnotationTagChips
import com.huangder.lumibooks.ui.components.FeaturePlaceholder
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace

/**
 * 知识页：时间倒序卡片流。每张卡片 = 原文引用 + 个人理解 + 出处，
 * 点卡片打开对应书（定位到具体位置在 V0.3 引入）。
 */
@Composable
fun KnowledgeScreen(
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeViewModel = hiltViewModel()
) {
    val cards by viewModel.visibleCards.collectAsState()
    val allTags by viewModel.tags.collectAsState()
    val selectedTag by viewModel.selectedTag.collectAsState()
    val allCount by viewModel.cards.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .statusBarsPadding()
    ) {
        Text(
            stringResource(R.string.nav_knowledge),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary,
            modifier = Modifier.padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
        )

        if (allCount.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                FeaturePlaceholder(
                    title = stringResource(R.string.nav_knowledge),
                    icon = AppIcons.KnowledgeTab.resolve(false),
                    headline = stringResource(R.string.knowledge_empty_title),
                    body = stringResource(R.string.knowledge_empty_body),
                    footnote = stringResource(R.string.knowledge_empty_footnote)
                )
            }
            return@Column
        }

        // 标签筛选：横向滚动 chips，复用 annotation_tag_all 文案。
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = AppSpace.lg),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                label = stringResource(R.string.annotation_tag_all),
                selected = selectedTag == null,
                onClick = { viewModel.setTag(null) }
            )
            allTags.forEach { tag ->
                FilterChip(
                    label = tag,
                    selected = selectedTag == tag,
                    onClick = { viewModel.setTag(if (selectedTag == tag) null else tag) }
                )
            }
        }

        Spacer(Modifier.height(AppSpace.sm))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = AppSpace.lg, vertical = AppSpace.sm
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            items(cards, key = { it.node.syncId.ifBlank { "node-${it.node.id}" } }) { card ->
                KnowledgeCardItem(card = card, onClick = { onOpenBook(card.evidence.bookId) })
            }
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AppColors.Accent else AppColors.BgGray)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) AppColors.OnAccent else AppColors.TextPrimary
        )
    }
}

@Composable
private fun KnowledgeCardItem(card: KnowledgeCard, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .clickable(onClick = onClick)
            .padding(AppSpace.md)
    ) {
        // 原文引用：左侧竖线 + 引文
        Row {
            Box(
                Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(AppColors.Accent.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                card.evidence.selectedText.replace('\n', ' '),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = AppColors.TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        // 个人理解（可空）
        if (card.node.thought.isNotBlank()) {
            Spacer(Modifier.height(AppSpace.sm))
            Text(
                card.node.thought,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = AppColors.TextSecondary
            )
        }

        Spacer(Modifier.height(AppSpace.sm))

        // 出处：书名 · 章节（书已删除时显示未定位兜底）
        val footer = when {
            card.bookTitle == null -> stringResource(R.string.note_unanchored_chapter)
            else -> {
                val chapter = card.evidence.chapterTitle?.takeIf { it.isNotBlank() }
                    ?: card.evidence.chapterIndex
                        .takeIf { it >= 0 }
                        ?.let { stringResource(R.string.chapter_number, it) }
                if (chapter != null) "${card.bookTitle} · $chapter" else card.bookTitle!!
            }
        }
        Text(
            footer,
            fontSize = 12.sp,
            color = AppColors.TextSecondary.copy(alpha = 0.8f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (card.node.tags.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.sm))
            AnnotationTagChips(card.node.tags)
        }
    }
}
