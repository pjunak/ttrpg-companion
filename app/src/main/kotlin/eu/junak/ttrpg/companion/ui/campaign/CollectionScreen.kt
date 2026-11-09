package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.Choice
import eu.junak.ttrpg.companion.core.model.normalizeIdentity
import eu.junak.ttrpg.companion.core.model.summary
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.RecordRow
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.components.partyName
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/** A filter chip's choice: the party, or one character status. */
private const val PARTY_FILTER = "party"
private const val STATUS_FILTER = "status:"

/**
 * The visible records of one page in the website's order, filtered by name
 * and, for characters, by party membership or status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    page: CampaignPage,
    onBack: () -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    val records = remember(state.dataset, page) { state.dataset?.records(page.collection).orEmpty() }
    val shown = remember(records, query, filter) {
        val needle = normalizeIdentity(query)
        records.filter { record ->
            val value = record.value
            val nameMatches = needle.isEmpty() || needle in normalizeIdentity(value.text("name").ifEmpty { record.key })
            val filterMatches = when {
                filter == null -> true
                filter == PARTY_FILTER -> value.text("faction") == "party"
                else -> value.text("status") == filter!!.removePrefix(STATUS_FILTER)
            }
            nameMatches && filterMatches
        }
    }
    val statuses = remember(records, settings) {
        if (page != CampaignPage.CHARACTERS) {
            emptyList()
        } else {
            val used = records.mapTo(HashSet()) { it.value.text("status") }
            settings?.characterStatuses.orEmpty().filter { it.id in used }
        }
    }
    val hasParty = page == CampaignPage.CHARACTERS && records.any { it.value.text("faction") == "party" }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(pageLabel(page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (records.size > FILTER_THRESHOLD) {
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(Icons.Default.FilterList, contentDescription = stringResource(R.string.collection_filter))
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStatus(state, onSignIn = {})
            if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text(stringResource(R.string.collection_filter_hint, pageLabel(page))) },
                    singleLine = true,
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.action_clear))
                            }
                        }
                    },
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.xs),
                )
            }
            if (hasParty || statuses.size > 1) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    if (hasParty) {
                        item {
                            FilterChip(
                                selected = filter == PARTY_FILTER,
                                onClick = { filter = if (filter == PARTY_FILTER) null else PARTY_FILTER },
                                label = { Text(partyName(settings?.party?.name.orEmpty())) },
                            )
                        }
                    }
                    // One status alone filters nothing.
                    items(if (statuses.size > 1) statuses else emptyList(), key = { it.id }) { status ->
                        val id = STATUS_FILTER + status.id
                        FilterChip(
                            selected = filter == id,
                            onClick = { filter = if (filter == id) null else id },
                            label = { Text(status.label) },
                            leadingIcon = { StatusDot(optionColor(status.color, MaterialTheme.colorScheme.primary)) },
                        )
                    }
                }
            }
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                if (shown.isEmpty()) {
                    CenteredMessage(stringResource(if (records.isEmpty()) R.string.collection_empty else R.string.collection_no_match))
                } else {
                    val baseUrl = site?.baseUrl.orEmpty()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = spacing.lg)) {
                        // Only a status other than the first (usual) one is marked, as the website marks the dead and missing.
                        val usualStatus = settings?.characterStatuses?.firstOrNull()?.id
                        items(shown, key = { it.key }) { record ->
                            val status = settings?.characterStatus(record.value.text("status"))?.takeIf { it.id != usualStatus }
                            RecordRow(
                                baseUrl,
                                record.summary(page),
                                trailing = status?.let { { StatusMark(it) } },
                            ) { onOpenRecord(page, record.key) }
                        }
                    }
                }
            }
        }
    }
}

/** A status's own symbol in its colour, or a dot when it has none. */
@Composable
private fun StatusMark(status: Choice) {
    val color = optionColor(status.color, MaterialTheme.colorScheme.primary)
    if (status.icon.isEmpty()) {
        StatusDot(color)
    } else {
        Text(status.icon, color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { contentDescription = status.label })
    }
}

/** Pages longer than this offer a name filter. */
private const val FILTER_THRESHOLD = 6
