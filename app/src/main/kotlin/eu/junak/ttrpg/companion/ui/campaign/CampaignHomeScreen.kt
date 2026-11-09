package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/** A site's start screen: its campaign's record pages with their sizes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CampaignHomeScreen(
    onBack: () -> Unit,
    onOpenPage: (CampaignPage) -> Unit,
    onSearch: () -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    onSignIn: () -> Unit,
    onSettings: () -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(state.dataset == null) { viewModel.ensureLoaded() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            state.dataset?.name?.takeIf(String::isNotEmpty) ?: site?.name.orEmpty(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                when (site?.role) {
                                    Role.DM -> R.string.role_dm
                                    Role.PLAYER -> R.string.role_player
                                    null -> R.string.role_reader
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
            )
        },
    ) { padding ->
        val dataset = state.dataset
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStatus(state, onSignIn)
            if (dataset == null) {
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
                LazyColumn(Modifier.fillMaxSize()) {
                    items(CampaignPage.entries, key = { it.id }) { page ->
                        val count = dataset.records(page.collection).size
                        ListItem(
                            headlineContent = { Text(pageLabel(page)) },
                            leadingContent = {
                                Box(Modifier.width(LocalSpacing.current.portrait), contentAlignment = Alignment.Center) {
                                    Text(page.icon, style = MaterialTheme.typography.titleLarge)
                                }
                            },
                            trailingContent = { Text(count.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            modifier = Modifier.clickable { onOpenPage(page) },
                        )
                    }
                }
            }
        }
    }
}
