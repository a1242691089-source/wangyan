package com.huangder.lumibooks.ui.knowledge

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.domain.model.KnowledgeEvidence
import com.huangder.lumibooks.domain.model.KnowledgeNodeDetail
import com.huangder.lumibooks.domain.model.KnowledgeNodeSummary
import com.huangder.lumibooks.domain.model.KnowledgeRelationType
import com.huangder.lumibooks.domain.model.KnowledgeRelationView
import com.huangder.lumibooks.ui.components.AnnotationTagChips
import com.huangder.lumibooks.ui.components.LiquidGlassAlertDialog
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.accentColor
import com.huangder.lumibooks.ui.components.labelRes
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType

/**
 * Knowledge node detail: read-only node content plus its evidence chain and
 * bidirectional relations. Editing the thought stays out of V0.3a.
 */
@Composable
fun KnowledgeDetailScreen(
    onNavigateBack: () -> Unit,
    onOpenEvidence: (bookId: String, chapterIndex: Int, startPosition: Int) -> Unit,
    onOpenNode: (Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeDetailViewModel = hiltViewModel()
) {
    val detail by viewModel.detail.collectAsState()
    val targets by viewModel.targets.collectAsState()
    val context = LocalContext.current
    var showAddRelation by remember { mutableStateOf(false) }
    var showDeleteNode by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                KnowledgeDetailEvent.RELATION_EXISTS ->
                    Toast.makeText(context, R.string.knowledge_detail_relation_exists, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
        ) {
            LiquidGlassIconButton(
                imageVector = directionalIcon(AppIcons.ArrowLeft, AppIcons.ArrowRight),
                contentDescription = stringResource(R.string.back),
                onClick = onNavigateBack,
                size = 40.dp,
                iconSize = 18.dp,
                contentColor = AppColors.TextPrimary,
                normalContainerColor = AppColors.BgGray,
                // P0 hotfix (v1.0.2-rc1 device validation): rendering these buttons
                // through the liquid-glass pipeline inside a NavHost destination blows
                // the RenderThread stack (RenderNode::prepareTreeImpl recursion,
                // SIGSEGV) on Android 16 — reproducible 100% on Galaxy S24 Ultra when
                // opening this screen. Exact library/device interaction unresolved
                // (backdrop 1.0.6 recorded-layer + offscreen lens layer); plain
                // buttons verified crash-free. Re-enable glass here only after the
                // backdrop upgrade/root-cause fix.
                forceFallback = true
            )
            Spacer(Modifier.width(AppSpace.md))
            Text(
                stringResource(R.string.knowledge_detail_title),
                fontSize = AppType.Section,
                fontWeight = FontWeight.Bold,
                color = AppColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                LiquidGlassIconButton(
                    imageVector = AppIcons.Trash,
                    contentDescription = stringResource(R.string.delete),
                    onClick = { showDeleteNode = true },
                    size = 40.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.Danger,
                    normalContainerColor = AppColors.BgGray,
                    // See the back button above: glass pipeline crashes RenderThread
                    // inside NavHost destinations on Android 16 (P0 hotfix).
                    forceFallback = true
                )
            }
        }

        val current = detail
        if (current == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.knowledge_detail_missing),
                    fontSize = AppType.Body,
                    color = AppColors.TextSecondary
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppSpace.lg, end = AppSpace.lg, bottom = AppSpace.xl
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            item {
                NodeHeader(current)
            }
            item {
                SectionTitle(stringResource(R.string.knowledge_detail_evidence))
            }
            if (current.evidences.isEmpty()) {
                item { HintText(stringResource(R.string.knowledge_detail_no_evidence)) }
            } else {
                items(current.evidences, key = { it.syncId.ifBlank { "ev-${it.id}" } }) { evidence ->
                    EvidenceCard(evidence = evidence, onOpen = onOpenEvidence)
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle(stringResource(R.string.knowledge_detail_relations), Modifier.weight(1f))
                    Text(
                        stringResource(R.string.knowledge_detail_add_relation),
                        fontSize = 13.sp,
                        color = AppColors.Accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable {
                                viewModel.loadTargets()
                                showAddRelation = true
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
            if (current.relations.isEmpty()) {
                item { HintText(stringResource(R.string.knowledge_detail_no_relations)) }
            } else {
                items(current.relations, key = { "rel-${it.relation.id}" }) { view ->
                    RelationRow(
                        view = view,
                        onOpenNode = onOpenNode,
                        onDelete = { viewModel.deleteRelation(view.relation) }
                    )
                }
            }
        }
    }

    if (showAddRelation) {
        AddRelationSheet(
            targets = targets,
            excludeNodeId = viewModel.nodeId,
            onPick = { target, type -> viewModel.addRelation(target.id, type) },
            onDismiss = { showAddRelation = false }
        )
    }

    if (showDeleteNode) {
        LiquidGlassAlertDialog(
            onDismissRequest = { showDeleteNode = false },
            title = { Text(stringResource(R.string.knowledge_detail_delete_title)) },
            text = { Text(stringResource(R.string.knowledge_detail_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteNode = false
                    viewModel.deleteNode(onDeleted = onNavigateBack)
                }) {
                    Text(stringResource(R.string.delete), color = AppColors.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteNode = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun NodeHeader(detail: KnowledgeNodeDetail) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        if (detail.node.title.isNotBlank()) {
            Text(
                detail.node.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 25.sp,
                color = AppColors.TextPrimary
            )
        }
        if (detail.node.thought.isNotBlank()) {
            if (detail.node.title.isNotBlank()) Spacer(Modifier.height(AppSpace.sm))
            Text(
                detail.node.thought,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = AppColors.TextSecondary
            )
        }
        if (detail.node.tags.isNotEmpty()) {
            Spacer(Modifier.height(AppSpace.sm))
            AnnotationTagChips(detail.node.tags)
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = AppColors.TextSecondary,
        modifier = modifier.padding(top = AppSpace.sm),
        maxLines = 1
    )
}

@Composable
private fun HintText(text: String) {
    Text(text, fontSize = 13.sp, color = AppColors.TextSecondary.copy(alpha = 0.8f))
}

@Composable
private fun EvidenceCard(
    evidence: KnowledgeEvidence,
    onOpen: (String, Int, Int) -> Unit
) {
    val target = evidenceJumpTarget(evidence)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.CardBg)
            .padding(AppSpace.md)
    ) {
        Row {
            Box(
                Modifier
                    .width(3.dp)
                    .height(36.dp)
                    .background(AppColors.Accent.copy(alpha = 0.45f))
            )
            Spacer(Modifier.width(10.dp))
            Text(
                evidence.selectedText.replace('\n', ' '),
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = AppColors.TextPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(AppSpace.sm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val chapter = evidence.chapterTitle?.takeIf { it.isNotBlank() }
                ?: evidence.chapterIndex
                    .takeIf { it >= 0 }
                    ?.let { stringResource(R.string.chapter_number, it) }
                .orEmpty()
            Text(
                chapter.ifBlank { stringResource(R.string.note_unanchored_chapter) },
                fontSize = 12.sp,
                color = AppColors.TextSecondary.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (target != null) {
                Text(
                    stringResource(R.string.knowledge_detail_open_reader),
                    fontSize = 12.sp,
                    color = AppColors.Accent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onOpen(evidence.bookId, target.first, target.second) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun RelationRow(
    view: KnowledgeRelationView,
    onOpenNode: (Long) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.CardBg)
            .padding(horizontal = AppSpace.md, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (view.isOutgoing) AppIcons.ArrowRight else AppIcons.ArrowLeft,
            contentDescription = null,
            tint = view.relation.type.accentColor(),
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(view.relation.type.labelRes()),
            fontSize = 12.sp,
            color = view.relation.type.accentColor(),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(view.relation.type.accentColor().copy(alpha = 0.12f))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            view.counterpartTitle.ifBlank { stringResource(R.string.knowledge_detail_untitled) },
            fontSize = 14.sp,
            color = AppColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenNode(view.counterpartNodeId) }
        )
        Icon(
            imageVector = AppIcons.X,
            contentDescription = stringResource(R.string.delete),
            tint = AppColors.TextSecondary.copy(alpha = 0.6f),
            modifier = Modifier
                .size(20.dp)
                .clickable(onClick = onDelete)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddRelationSheet(
    targets: List<KnowledgeNodeSummary>,
    excludeNodeId: Long,
    onPick: (KnowledgeNodeSummary, KnowledgeRelationType) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var chosenType by remember { mutableStateOf<KnowledgeRelationType?>(null) }
    var query by remember { mutableStateOf("") }
    val visible = filterRelationTargets(targets, query, excludeNodeId)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.CardBg,
        contentColor = AppColors.TextPrimary,
        scrimColor = Color.Black.copy(alpha = 0.12f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpace.lg)
                .padding(bottom = AppSpace.xl)
        ) {
            Text(
                stringResource(
                    if (chosenType == null) R.string.knowledge_detail_pick_type
                    else R.string.knowledge_detail_pick_node
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.TextPrimary
            )
            Spacer(Modifier.height(AppSpace.md))

            if (chosenType == null) {
                KnowledgeRelationType.entries.chunked(3).forEach { rowTypes ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowTypes.forEach { type ->
                            Text(
                                stringResource(type.labelRes()),
                                fontSize = 13.sp,
                                color = type.accentColor(),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(type.accentColor().copy(alpha = 0.12f))
                                    .clickable { chosenType = type }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            } else {
                val type = chosenType!!
                Text(
                    stringResource(type.labelRes()),
                    fontSize = 13.sp,
                    color = type.accentColor(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(type.accentColor().copy(alpha = 0.12f))
                        .clickable { chosenType = null }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Spacer(Modifier.height(AppSpace.sm))
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 14.sp, color = AppColors.TextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.BgGray)
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                Spacer(Modifier.height(AppSpace.sm))
                if (visible.isEmpty()) {
                    HintText(stringResource(R.string.knowledge_detail_no_targets))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(visible, key = { it.id }) { summary ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        onPick(summary, type)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 4.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    summary.title.ifBlank { stringResource(R.string.knowledge_detail_untitled) },
                                    fontSize = 14.sp,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (summary.thought.isNotBlank()) {
                                    Text(
                                        summary.thought,
                                        fontSize = 12.sp,
                                        color = AppColors.TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
