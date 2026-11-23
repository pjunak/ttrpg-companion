package eu.junak.ttrpg.companion.ui.map

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.ui.campaign.CampaignMissing
import eu.junak.ttrpg.companion.ui.campaign.CampaignStatus
import eu.junak.ttrpg.companion.ui.campaign.hostFailureText
import eu.junak.ttrpg.companion.ui.campaign.CampaignViewModel
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Notice

/**
 * The world map or a location's own map, natively: the host's tiles (or the
 * original image), the location pins this session may see and, on request,
 * the story trail of events. Tapping a marker opens its record.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    onSignIn: () -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
    campaign: CampaignViewModel = hiltViewModel(),
) {
    val site by campaign.site.collectAsStateWithLifecycle()
    val state by campaign.state.collectAsStateWithLifecycle()
    val map by viewModel.map.collectAsStateWithLifecycle()
    val background by viewModel.background.collectAsStateWithLifecycle()
    var showEvents by rememberSaveable { mutableStateOf(false) }
    val parentId = viewModel.parentId
    val title = if (parentId == null) {
        stringResource(R.string.map_world)
    } else {
        map?.parent?.name?.let { stringResource(R.string.map_local, it) } ?: stringResource(R.string.record_map)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (map?.events?.isNotEmpty() == true) {
                        IconToggleButton(checked = showEvents, onCheckedChange = { showEvents = it }) {
                            Icon(Icons.Filled.Route, contentDescription = stringResource(R.string.map_event_paths))
                        }
                    }
                    IconButton(onClick = { onOpenWebsite(if (parentId == null) "/map/world" else "/map/local/${Uri.encode(parentId)}") }) {
                        Icon(Icons.Filled.Public, contentDescription = stringResource(R.string.record_open_website))
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (parentId != null && state.dataset == null && state.problem != null && !state.refreshing) {
                CampaignMissing(state, campaign::refresh)
                return@Box
            }
            when (val shown = background) {
                MapBackground.Loading -> CenteredMessage(null, loading = true)
                MapBackground.Unavailable -> CenteredMessage(stringResource(R.string.map_unavailable))
                MapBackground.NoImage -> CenteredMessage(stringResource(if (parentId == null) R.string.map_no_world else R.string.map_no_local))
                is MapBackground.Failed -> CenteredMessage(stringResource(R.string.map_failed, hostFailureText(shown.failure))) {
                    TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.action_retry)) }
                }
                is MapBackground.Ready -> {
                    MapView(
                        image = shown.image,
                        baseUrl = site?.baseUrl.orEmpty(),
                        map = map,
                        camera = viewModel.camera,
                        tiles = viewModel.tiles,
                        showEvents = showEvents,
                        focus = viewModel.focus,
                        onOpen = { onOpenRecord(it.page, it.key) },
                        onTilesRefused = { viewModel.tilesRefused(shown.image.mediaPath) },
                    )
                    Column(Modifier.align(Alignment.TopCenter)) {
                        if (shown.offline) Notice(stringResource(R.string.map_offline)) else CampaignStatus(state, onSignIn)
                    }
                }
            }
        }
    }
}
