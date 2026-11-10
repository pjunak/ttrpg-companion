package eu.junak.ttrpg.companion.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.RecordSummary
import eu.junak.ttrpg.companion.core.model.details
import eu.junak.ttrpg.companion.core.model.latestSession
import eu.junak.ttrpg.companion.core.model.sessions
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.campaign.CampaignStatus
import eu.junak.ttrpg.companion.ui.campaign.CampaignViewModel
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Notice
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/** Every session, newest first, with a button that starts the next one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    onBack: () -> Unit,
    onOpenSession: (Int) -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val site by viewModel.site.collectAsStateWithLifecycle()
    val sessions = remember(state.dataset) { state.dataset?.sessions().orEmpty() }
    val latest = remember(state.dataset) { state.dataset?.latestSession() ?: 0 }
    val canWrite = site?.role != null
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.sessions_title)) },
                navigationIcon = { BackButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (canWrite && state.dataset != null) {
                ExtendedFloatingActionButton(
                    onClick = { onOpenSession(latest + 1) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.sessions_new, latest + 1)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStatus(state, onSignIn = {})
            when {
                state.dataset == null -> CenteredMessage(stringResource(R.string.campaign_loading), loading = state.refreshing)
                sessions.isEmpty() -> CenteredMessage(stringResource(R.string.sessions_empty))
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.xl * 3),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    items(sessions, key = { it.number }) { session ->
                        Surface(
                            onClick = { onOpenSession(session.number) },
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        stringResource(R.string.session_title, session.number),
                                        style = MaterialTheme.typography.titleLarge,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        pluralStringResource(R.plurals.session_events, session.events.size, session.events.size),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (session.events.isNotEmpty()) {
                                    Text(
                                        session.events.joinToString(" · ") { it.name },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
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

/**
 * One session as a running log: its events in order, each opening its
 * record, and a composer at the bottom whose first line becomes a new event's
 * name. This is the quick way to take notes at the table.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    number: Int,
    onBack: () -> Unit,
    onOpenEvent: (String) -> Unit,
    onEditEvent: (String) -> Unit,
    onFullEditor: (note: String) -> Unit,
    onSignIn: () -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
    notes: SessionNotesViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val site by viewModel.site.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val text by notes.text.collectAsStateWithLifecycle()
    val sending by notes.sending.collectAsStateWithLifecycle()
    val problem by notes.problem.collectAsStateWithLifecycle()
    val dataset = state.dataset
    val events = remember(dataset, number) { dataset?.sessions()?.firstOrNull { it.number == number }?.events.orEmpty() }
    val canWrite = site?.role != null
    val list = rememberLazyListState()
    LaunchedEffect(events.size) { if (events.isNotEmpty()) list.animateScrollToItem(events.lastIndex) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.session_title, number)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
        bottomBar = {
            if (canWrite) {
                NoteComposer(
                    text = text,
                    sending = sending,
                    problem = problem,
                    onText = notes::setText,
                    onSend = notes::send,
                    onFullEditor = {
                        onFullEditor(text)
                        notes.setText("")
                    },
                )
            } else {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.navigationBarsPadding()) {
                    Notice(stringResource(R.string.session_sign_in_to_write)) {
                        TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in_again)) }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStatus(state, onSignIn)
            if (dataset == null) {
                CenteredMessage(stringResource(R.string.campaign_loading), loading = state.refreshing)
                return@Column
            }
            if (events.isEmpty()) {
                CenteredMessage(stringResource(if (canWrite) R.string.session_empty else R.string.session_empty_reader))
                return@Column
            }
            LazyColumn(
                state = list,
                contentPadding = PaddingValues(spacing.md),
                verticalArrangement = Arrangement.spacedBy(spacing.sm),
            ) {
                items(events, key = { it.key }) { event ->
                    EventCard(
                        baseUrl = site?.baseUrl.orEmpty(),
                        event = event,
                        priorityColor = dataset.record(CampaignPage.EVENTS.collection, event.key)
                            ?.value?.text("priority")
                            ?.let { settings?.eventPriority(it)?.color },
                        linked = remember(dataset, event.key) {
                            dataset.details(CampaignPage.EVENTS, event.key)?.related?.flatMap { it.records }.orEmpty()
                        },
                        canEdit = canWrite,
                        onOpen = { onOpenEvent(event.key) },
                        onEdit = { onEditEvent(event.key) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EventCard(
    baseUrl: String,
    event: RecordSummary,
    priorityColor: Long?,
    linked: List<RecordRef>,
    canEdit: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = spacing.md, bottom = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (priorityColor != null) {
                    StatusDot(optionColor(priorityColor, MaterialTheme.colorScheme.primary))
                    Spacer(Modifier.size(spacing.sm))
                }
                Text(
                    event.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(top = spacing.md),
                )
                if (canEdit) {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.event_edit))
                    }
                } else {
                    Spacer(Modifier.size(spacing.md))
                }
            }
            val text = listOf(event.subtitle, event.excerpt).filter(String::isNotEmpty).distinct().joinToString(" · ")
            if (text.isNotEmpty()) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = spacing.md),
                )
            }
            if (linked.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.xs), modifier = Modifier.padding(end = spacing.md)) {
                    linked.forEach { ref ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = spacing.xxs)) {
                            Portrait(baseUrl, ref.portrait, ref.name, ref.page, spacing.md + spacing.xs, ref.icon)
                            Spacer(Modifier.size(spacing.xs))
                            Text(ref.name, style = MaterialTheme.typography.labelMedium)
                            Spacer(Modifier.size(spacing.sm))
                        }
                    }
                }
            }
        }
    }
}

/** The bottom bar where notes are written during play. */
@Composable
private fun NoteComposer(
    text: String,
    sending: Boolean,
    problem: SaveProblem?,
    onText: (String) -> Unit,
    onSend: () -> Unit,
    onFullEditor: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = spacing.sm, vertical = spacing.sm)) {
            if (problem != null) {
                Text(
                    saveProblemText(problem),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs),
                )
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onFullEditor, enabled = !sending) {
                    Icon(Icons.Default.OpenInFull, contentDescription = stringResource(R.string.note_full_editor))
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = onText,
                    placeholder = { Text(stringResource(R.string.note_hint)) },
                    maxLines = 6,
                    enabled = !sending,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.size(spacing.xs))
                if (sending) {
                    CircularProgressIndicator(Modifier.padding(spacing.sm).size(spacing.lg + spacing.xs))
                } else {
                    FilledIconButton(onClick = onSend, enabled = text.isNotBlank(), modifier = Modifier.padding(bottom = spacing.xs)) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.note_send))
                    }
                }
            }
        }
    }
}
