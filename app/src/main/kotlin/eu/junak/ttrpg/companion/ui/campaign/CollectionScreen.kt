package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.summaries
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.RecordRow
import eu.junak.ttrpg.companion.ui.components.pageLabel

/** The visible records of one page, in the web app's order. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionScreen(
    page: CampaignPage,
    onBack: () -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val records = remember(state.dataset, page) { state.dataset?.summaries(page).orEmpty() }

    Scaffold(topBar = { TopAppBar(title = { Text(pageLabel(page)) }, navigationIcon = { BackButton(onBack) }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            CampaignStatus(state, onSignIn = {})
            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = viewModel::refresh) {
                if (records.isEmpty()) {
                    CenteredMessage(stringResource(R.string.collection_empty))
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(records, key = { it.key }) { summary ->
                            RecordRow(site?.baseUrl.orEmpty(), summary) { onOpenRecord(page, summary.key) }
                        }
                    }
                }
            }
        }
    }
}
