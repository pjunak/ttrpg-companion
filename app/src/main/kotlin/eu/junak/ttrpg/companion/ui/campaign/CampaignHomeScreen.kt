package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignOverview
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.RecordSummary
import eu.junak.ttrpg.companion.core.model.overview
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.PortraitTile
import eu.junak.ttrpg.companion.ui.components.RecordRow
import eu.junak.ttrpg.companion.ui.components.SectionCard
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.pageIcon
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.components.partyName
import eu.junak.ttrpg.companion.ui.components.relativeTime
import eu.junak.ttrpg.companion.ui.components.roleLabel
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * A site's start screen, like the website's dashboard: the party, the last
 * session, what changed recently, what the reader opened last and every page
 * that has records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignHomeScreen(
    onBack: () -> Unit,
    onOpenPage: (CampaignPage) -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    onOpenSession: (Int) -> Unit,
    onOpenSessions: () -> Unit,
    onSearch: () -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val recentlyOpened by viewModel.recentlyOpened.collectAsStateWithLifecycle()
    val overview = remember(state.dataset) { state.dataset?.overview(recentLimit = 6) }
    val baseUrl = site?.baseUrl.orEmpty()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.dataset == null) { viewModel.ensureLoaded() }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        overview?.name?.takeIf(String::isNotEmpty) ?: site?.name.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = onSearch) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search))
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_refresh)) },
                            onClick = { menuOpen = false; viewModel.refresh() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_website)) },
                            onClick = { menuOpen = false; onOpenWebsite("/") },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_sign_in_again)) },
                            onClick = { menuOpen = false; onSignIn() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_settings)) },
                            onClick = { menuOpen = false; onSettings() },
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (overview == null) {
                CampaignStatus(state, onSignIn)
                val problem = state.problem
                CenteredMessage(
                    text = if (problem is CampaignProblem.Unavailable) {
                        stringResource(R.string.campaign_failed, problem.detail)
                    } else {
                        stringResource(R.string.campaign_loading)
                    },
                    loading = state.refreshing,
                )
                return@Column
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.xl)) {
                    item { Introduction(overview, roleLabel(site?.role), state.fetchedAt) }
                    item { CampaignStatus(state, onSignIn) }
                    item { SearchField(onSearch) }
                    if (overview.party.isNotEmpty() || overview.companions.isNotEmpty()) {
                        item {
                            SectionHeader(partyName(settings?.party?.name.orEmpty()), Modifier.padding(horizontal = spacing.md))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = spacing.sm),
                                horizontalArrangement = Arrangement.spacedBy(spacing.xs),
                            ) {
                                items(overview.party + overview.companions, key = { "${it.page.id}/${it.key}" }) { member ->
                                    PortraitTile(baseUrl, member.ref()) { onOpenRecord(member.page, member.key) }
                                }
                            }
                        }
                    }
                    val canWrite = site?.role != null
                    if (overview.lastSession != null || canWrite) {
                        item { SessionSection(baseUrl, overview, canWrite, onOpenRecord, onOpenSession, onOpenSessions) }
                    }
                    if (overview.recent.isNotEmpty()) {
                        item {
                            Column(Modifier.padding(horizontal = spacing.md)) {
                                SectionHeader(stringResource(R.string.home_recent_changes))
                                SectionCard {
                                    overview.recent.forEach { change ->
                                        val summary = change.summary
                                        val time = relativeTime(change.activity.at)
                                        val what = stringResource(
                                            if (change.activity.created) R.string.change_created else R.string.change_updated,
                                            time,
                                        )
                                        RecordRow(baseUrl, summary, supporting = "${pageLabel(summary.page)} · $what") {
                                            onOpenRecord(summary.page, summary.key)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (recentlyOpened.isNotEmpty()) {
                        item { ContinueReading(baseUrl, recentlyOpened, onOpenRecord) }
                    }
                    item { PageGrid(overview, onOpenPage) }
                    item {
                        OutlinedButton(
                            onClick = { onOpenWebsite("/") },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.lg),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(spacing.md + spacing.xxs))
                            Spacer(Modifier.size(spacing.sm))
                            Text(stringResource(R.string.action_website))
                        }
                    }
                }
            }
        }
    }
}

/**
 * The latest session's events with the way into session notes: add a note to
 * that session, or see every session. Before the first event, a signed-in
 * reader can start session 1.
 */
@Composable
private fun SessionSection(
    baseUrl: String,
    overview: CampaignOverview,
    canWrite: Boolean,
    onOpenRecord: (CampaignPage, String) -> Unit,
    onOpenSession: (Int) -> Unit,
    onOpenSessions: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val last = overview.lastSession
    Column(Modifier.padding(horizontal = spacing.md)) {
        SectionHeader(if (last != null) stringResource(R.string.home_last_session, last) else stringResource(R.string.sessions_title))
        SectionCard {
            overview.lastSessionEvents.forEach { event ->
                RecordRow(baseUrl, event) { onOpenRecord(event.page, event.key) }
            }
            if (last == null) {
                Text(
                    stringResource(R.string.sessions_empty),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.md, vertical = spacing.sm),
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
                horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.End),
            ) {
                if (last != null) {
                    TextButton(onClick = onOpenSessions) { Text(stringResource(R.string.home_all_sessions)) }
                }
                if (canWrite) {
                    FilledTonalButton(onClick = { onOpenSession(last ?: 1) }) {
                        Icon(Icons.Default.EditNote, contentDescription = null, modifier = Modifier.size(spacing.md + spacing.xs))
                        Spacer(Modifier.size(spacing.sm))
                        Text(stringResource(if (last != null) R.string.home_add_note else R.string.home_start_notes))
                    }
                }
            }
        }
    }
}

@Composable
private fun Introduction(overview: CampaignOverview, role: String, fetchedAt: Long?) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        if (overview.tagline.isNotEmpty()) {
            Text(overview.tagline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val updated = fetchedAt?.let { stringResource(R.string.home_synced, relativeTime(it)) }
        Text(
            listOfNotNull(role, updated).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Looks like a search field and opens the search screen. */
@Composable
private fun SearchField(onSearch: () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onSearch,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Row(Modifier.padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xs), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(spacing.sm))
            Text(stringResource(R.string.home_search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContinueReading(baseUrl: String, records: List<RecordRef>, onOpenRecord: (CampaignPage, String) -> Unit) {
    val spacing = LocalSpacing.current
    Column(Modifier.padding(horizontal = spacing.md)) {
        SectionHeader(stringResource(R.string.home_continue_reading))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), maxLines = 3) {
            records.take(8).forEach { ref ->
                AssistChip(
                    onClick = { onOpenRecord(ref.page, ref.key) },
                    label = { Text(ref.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = { Portrait(baseUrl, ref.portrait, ref.name, ref.page, spacing.md + spacing.sm, ref.icon) },
                )
            }
        }
    }
}

/** Every page with records as a two-column grid of tiles with their counts. */
@Composable
private fun PageGrid(overview: CampaignOverview, onOpenPage: (CampaignPage) -> Unit) {
    val spacing = LocalSpacing.current
    val pages = CampaignPage.entries.filter { (overview.counts[it] ?: 0) > 0 }
    Column(Modifier.padding(horizontal = spacing.md), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        SectionHeader(stringResource(R.string.home_browse))
        pages.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                row.forEach { page -> PageTile(page, overview.counts[page] ?: 0, Modifier.weight(1f)) { onOpenPage(page) } }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun PageTile(page: CampaignPage, count: Int, modifier: Modifier, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.height(spacing.tileHeight),
    ) {
        Column(Modifier.padding(spacing.md), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(pageIcon(page), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(count.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(pageLabel(page), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun RecordSummary.ref() = RecordRef(page, key, name, portrait, icon)
