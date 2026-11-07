package com.huangder.lumibooks.ui.knowledge

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.huangder.lumibooks.R
import com.huangder.lumibooks.ai.openAiSettings
import com.huangder.lumibooks.domain.model.ChatMessageStatus
import com.huangder.lumibooks.domain.model.ChatRole
import com.huangder.lumibooks.domain.model.QaSourceType
import com.huangder.lumibooks.translation.messageResource
import com.huangder.lumibooks.ui.components.LiquidGlassIconButton
import com.huangder.lumibooks.ui.components.LocalLiquidGlassBackdrop
import com.huangder.lumibooks.ui.icons.AppIcons
import com.huangder.lumibooks.ui.icons.directionalIcon
import com.huangder.lumibooks.ui.theme.AppColors
import com.huangder.lumibooks.ui.theme.AppSpace
import com.huangder.lumibooks.ui.theme.AppType
import kotlinx.coroutines.delay

/**
 * Cross-book knowledge QA with citations (V0.5b). Messages render from the
 * DB flow only; a turn goes through pending → complete/failed rows written
 * by the repository, so reloads and process death both look honest.
 */
@Composable
fun KnowledgeQaScreen(
    onNavigateBack: () -> Unit,
    onOpenSource: (bookId: String, chapterIndex: Int, startPosition: Int) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: KnowledgeQaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val configuration by viewModel.configuration.collectAsState()
    val sessions by viewModel.sessions.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val input by viewModel.input.collectAsState()
    val sending by viewModel.sending.collectAsState()
    val showSessions by viewModel.showSessions.collectAsState()
    val notice by viewModel.notice.collectAsState()

    LaunchedEffect(notice) {
        when (val current = notice) {
            is QaNotice.NotConfigured -> {
                Toast.makeText(context, R.string.qa_unconfigured_title, Toast.LENGTH_SHORT).show()
                viewModel.consumeNotice()
            }
            is QaNotice.Error -> {
                Toast.makeText(context, current.error.messageResource(), Toast.LENGTH_SHORT).show()
                viewModel.consumeNotice()
            }
            null -> Unit
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(AppColors.PageBg)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Same glass self-sampling guard as KnowledgeDetailScreen: the NavHost
        // capture layer must never be sampled by a surface inside it (P0).
        CompositionLocalProvider(LocalLiquidGlassBackdrop provides null) {
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
                    normalContainerColor = AppColors.BgGray
                )
                Spacer(Modifier.width(AppSpace.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.qa_title),
                        fontSize = AppType.Section,
                        fontWeight = FontWeight.Bold,
                        color = AppColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        stringResource(R.string.qa_scope_all_books),
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary
                    )
                }
                LiquidGlassIconButton(
                    imageVector = AppIcons.List,
                    contentDescription = stringResource(R.string.qa_history),
                    onClick = { viewModel.toggleSessions(true) },
                    size = 40.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
                Spacer(Modifier.width(AppSpace.sm))
                LiquidGlassIconButton(
                    imageVector = AppIcons.Plus,
                    contentDescription = stringResource(R.string.qa_new_session),
                    onClick = { viewModel.startNewSession() },
                    size = 40.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }
        }

        val listState = rememberLazyListState()
        LaunchedEffect(messages.size) {
            if (messages.isNotEmpty()) {
                delay(50)
                listState.animateScrollToItem(messages.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = AppSpace.lg, end = AppSpace.lg, bottom = AppSpace.md
            ),
            verticalArrangement = Arrangement.spacedBy(AppSpace.md)
        ) {
            if (messages.isEmpty() && !sending) {
                item { EmptyState(viewModel = viewModel) }
            }
            items(messages, key = { it.message.id }) { ui ->
                when (ui.message.role) {
                    ChatRole.USER -> UserBubble(ui.message.content)
                    ChatRole.ASSISTANT -> AssistantBlock(
                        ui = ui,
                        onOpenSource = onOpenSource
                    )
                }
            }
        }

        val configured = configuration?.enabled == true
        if (configured) {
            Composer(
                input = input,
                sending = sending,
                onInput = viewModel::setInput,
                onSend = viewModel::send
            )
        } else {
            UnconfiguredCard()
        }

        if (showSessions) {
            SessionsOverlay(
                sessions = sessions,
                onSelect = viewModel::selectSession,
                onDelete = viewModel::deleteSession,
                onDismiss = { viewModel.toggleSessions(false) }
            )
        }
    }
}

@Composable
private fun EmptyState(viewModel: KnowledgeQaViewModel) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 48.dp)
    ) {
        Text(
            stringResource(R.string.qa_empty_title),
            fontSize = AppType.Section,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            stringResource(R.string.qa_empty_body),
            fontSize = AppType.Body,
            color = AppColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.lg))
        listOf(
            stringResource(R.string.qa_example_1),
            stringResource(R.string.qa_example_2),
            stringResource(R.string.qa_example_3)
        ).forEach { example ->
            Box(
                modifier = Modifier
                    .padding(vertical = AppSpace.sm / 2)
                    .clip(RoundedCornerShape(50))
                    .background(AppColors.BgGray)
                    .clickable {
                        viewModel.setInput(example)
                        viewModel.send()
                    }
                    .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
            ) {
                Text(example, fontSize = AppType.Caption, color = AppColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(AppSpace.lg))
        Text(
            stringResource(R.string.qa_privacy_hint),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun UserBubble(content: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.BgGray)
                .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
        ) {
            Text(content, fontSize = AppType.Body, color = AppColors.TextPrimary)
        }
    }
}

@Composable
private fun AssistantBlock(
    ui: QaMessageUi,
    onOpenSource: (String, Int, Int) -> Unit
) {
    val accent = AppColors.Accent
    val message = ui.message
    when (message.status) {
        ChatMessageStatus.PENDING -> if (ui.interrupted) {
            HintText(stringResource(R.string.qa_interrupted))
        } else {
            HintText(stringResource(R.string.qa_thinking))
        }
        ChatMessageStatus.FAILED -> HintText(stringResource(R.string.qa_interrupted))
        ChatMessageStatus.COMPLETE -> {
            val parsed = ui.parsed
            if (parsed == null) {
                HintText(message.content.ifBlank { stringResource(R.string.qa_interrupted) })
            } else {
                val onCiteClick: (Int) -> Unit = { number ->
                    message.citations.firstOrNull { it.n == number }?.let { citation ->
                        val target = citation.jumpTarget
                        val bookId = citation.bookId
                        if (target != null && bookId != null) {
                            onOpenSource(bookId, target.first, target.second)
                        }
                    }
                }
                AnswerBody(parsed = parsed, accent = accent, onCiteClick = onCiteClick)
                CitationHints(parsed = parsed, sourceCount = message.sourceCount)
                if (message.citations.isNotEmpty()) {
                    Text(
                        stringResource(R.string.qa_sources),
                        fontSize = AppType.Caption,
                        color = AppColors.TextSecondary,
                        modifier = Modifier.padding(top = AppSpace.sm)
                    )
                }
                message.citations.forEach { citation ->
                    CitationCard(citation = citation, onOpen = onOpenSource)
                }
            }
        }
    }
}

@Composable
private fun AnswerBody(
    parsed: com.huangder.lumibooks.ai.qa.CitationParser.ParsedAnswer,
    accent: androidx.compose.ui.graphics.Color,
    onCiteClick: (Int) -> Unit
) {
    val markerStyle = SpanStyle(
        color = accent,
        fontSize = 12.sp,
        baselineShift = BaselineShift.Superscript
    )
    val text = KnowledgeQaCitationUi.buildAnswerText(parsed, markerStyle)
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    BasicText(
        text = text,
        style = TextStyle(fontSize = AppType.Body, color = AppColors.TextPrimary, lineHeight = 24.sp),
        onTextLayout = { layoutResult = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(parsed) {
                detectTapGestures { position ->
                    val layout = layoutResult ?: return@detectTapGestures
                    // Same offset-hit pattern as the welcome links.
                    val offset = layout.getOffsetForPosition(position)
                    val annotations = text.getStringAnnotations(
                        tag = KnowledgeQaCitationUi.CITATION_TAG,
                        start = offset,
                        end = offset
                    )
                    annotations.firstOrNull()?.item?.toIntOrNull()?.let { number ->
                        onCiteClick(number)
                    }
                }
            }
    )
    val inferences = KnowledgeQaCitationUi.inferenceLines(parsed)
    inferences.forEach { line ->
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.padding(top = AppSpace.sm)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(AppColors.BgGray)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    stringResource(R.string.qa_inference),
                    fontSize = 10.sp,
                    color = AppColors.TextSecondary
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                line,
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CitationHints(parsed: com.huangder.lumibooks.ai.qa.CitationParser.ParsedAnswer, sourceCount: Int) {
    if (sourceCount == 0) {
        HintText(stringResource(R.string.qa_no_sources))
    } else if (parsed.citedIndices.isEmpty()) {
        HintText(stringResource(R.string.qa_uncited))
    }
}

@Composable
private fun CitationCard(
    citation: com.huangder.lumibooks.domain.model.Citation,
    onOpen: (String, Int, Int) -> Unit
) {
    val jumpable = citation.jumpTarget != null && citation.bookId != null
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.BgGray)
            .then(
                if (jumpable) {
                    Modifier.clickable {
                        val (chapter, position) = citation.jumpTarget!!
                        onOpen(citation.bookId!!, chapter, position)
                    }
                } else {
                    Modifier
                }
            )
            .padding(AppSpace.md)
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(40.dp)
                .background(AppColors.Accent)
        )
        Spacer(Modifier.width(AppSpace.sm))
        Column(Modifier.weight(1f)) {
            Text(
                citationOriginLine(citation),
                fontSize = AppType.Caption,
                color = AppColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                citation.quote,
                fontSize = AppType.Caption,
                color = AppColors.TextPrimary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (jumpable) {
            Text(
                stringResource(R.string.qa_view_source),
                fontSize = AppType.Caption,
                color = AppColors.Accent,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

private fun citationOriginLine(citation: com.huangder.lumibooks.domain.model.Citation): String = buildString {
    if (citation.bookTitle != null) {
        append("《").append(citation.bookTitle).append("》")
        if (citation.chapterIndex >= 0) append(" 第").append(citation.chapterIndex + 1).append("章")
        citation.chapterTitle?.takeIf { it.isNotBlank() }?.let { append("·").append(it) }
    } else {
        append("知识")
        citation.chapterTitle?.takeIf { it.isNotBlank() }?.let { append("·").append(it) }
    }
}

@Composable
private fun Composer(
    input: String,
    sending: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.sm)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(20.dp))
                .background(AppColors.BgGray)
                .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
        ) {
            if (input.isEmpty()) {
                Text(
                    stringResource(R.string.qa_input_hint),
                    fontSize = AppType.Body,
                    color = AppColors.TextSecondary
                )
            }
            BasicTextField(
                value = input,
                onValueChange = onInput,
                textStyle = TextStyle(fontSize = AppType.Body, color = AppColors.TextPrimary),
                cursorBrush = SolidColor(AppColors.Accent),
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(AppSpace.sm))
        LiquidGlassIconButton(
            imageVector = AppIcons.ArrowCircleDown,
            contentDescription = stringResource(R.string.qa_send),
            onClick = onSend,
            enabled = !sending && input.isNotBlank(),
            size = 40.dp,
            iconSize = 18.dp,
            contentColor = if (input.isNotBlank()) AppColors.Accent else AppColors.TextSecondary,
            normalContainerColor = AppColors.BgGray
        )
    }
}

@Composable
private fun UnconfiguredCard() {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpace.lg, vertical = AppSpace.md)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.BgGray)
            .padding(AppSpace.lg)
    ) {
        Text(
            stringResource(R.string.qa_unconfigured_title),
            fontSize = AppType.Body,
            fontWeight = FontWeight.Bold,
            color = AppColors.TextPrimary
        )
        Spacer(Modifier.height(AppSpace.sm))
        Text(
            stringResource(R.string.qa_unconfigured_body),
            fontSize = AppType.Caption,
            color = AppColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(AppSpace.md))
        Text(
            stringResource(R.string.qa_unconfigured_action),
            fontSize = AppType.Body,
            color = AppColors.Accent,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { openAiSettings(context) }
        )
    }
}

@Composable
private fun SessionsOverlay(
    sessions: List<com.huangder.lumibooks.domain.model.ChatSession>,
    onSelect: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // Opaque backdrop: PageBg resolves to transparent when a glass background
    // host is present, which would show the chat through the overlay.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.WindowBg)
            .clickable(onClick = onDismiss)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(enabled = false) { }
                .padding(AppSpace.lg)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.qa_sessions_title),
                    fontSize = AppType.Section,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                LiquidGlassIconButton(
                    imageVector = AppIcons.X,
                    contentDescription = stringResource(R.string.back),
                    onClick = onDismiss,
                    size = 40.dp,
                    iconSize = 18.dp,
                    contentColor = AppColors.TextPrimary,
                    normalContainerColor = AppColors.BgGray
                )
            }
            Spacer(Modifier.height(AppSpace.md))
            if (sessions.isEmpty()) {
                HintText(stringResource(R.string.qa_session_default_title))
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                    items(sessions, key = { it.id }) { session ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.BgGray)
                                .clickable { onSelect(session.id) }
                                .padding(horizontal = AppSpace.md, vertical = AppSpace.sm)
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    session.title.ifBlank { stringResource(R.string.qa_session_default_title) },
                                    fontSize = AppType.Body,
                                    color = AppColors.TextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            LiquidGlassIconButton(
                                imageVector = AppIcons.Trash,
                                contentDescription = stringResource(R.string.qa_delete_session),
                                onClick = { onDelete(session.id) },
                                size = 32.dp,
                                iconSize = 14.dp,
                                contentColor = AppColors.Danger,
                                normalContainerColor = AppColors.PageBg
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HintText(text: String) {
    Text(
        text,
        fontSize = AppType.Caption,
        color = AppColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
    )
}
