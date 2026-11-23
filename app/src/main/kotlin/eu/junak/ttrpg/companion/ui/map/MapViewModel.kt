package eu.junak.ttrpg.companion.ui.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.core.model.CampaignMap
import eu.junak.ttrpg.companion.core.model.campaignMap
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.core.network.maps.MapImage
import eu.junak.ttrpg.companion.core.network.maps.MapLoad
import eu.junak.ttrpg.companion.core.network.maps.MapRepository
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.navigation.MapRoute
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject

/** What a map screen shows behind its pins. */
sealed interface MapBackground {
    data object Loading : MapBackground

    /** The map's location is not in this session's campaign. */
    data object Unavailable : MapBackground

    /** The map has no image yet. */
    data object NoImage : MapBackground

    data class Failed(val failure: HostFailure) : MapBackground

    /** [offline] when the host could not be reached and saved details are shown. */
    data class Ready(val image: MapImage, val offline: Boolean) : MapBackground
}

/**
 * One map: its pins from the role-projected campaign (live, as the campaign
 * refreshes) and its image from the host, the world map's or the location's.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sites: SiteStore,
    campaigns: CampaignRepository,
    private val maps: MapRepository,
    /** Shared by every map screen, so they use one bounded tile cache. */
    val tiles: MapTileImages,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<MapRoute>()
    val parentId: String? = route.parentId
    val focus: String? = route.focus

    private val campaign: StateFlow<CampaignState> =
        sites.site(route.siteId)?.let(campaigns::state) ?: MutableStateFlow(CampaignState())

    /** Pins and events; null while the campaign loads or when the location is not visible. */
    val map: StateFlow<CampaignMap?> = campaign
        .map { it.dataset }
        .distinctUntilChanged()
        .map { it?.campaignMap(parentId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, campaign.value.dataset?.campaignMap(parentId))

    private val reloads = MutableStateFlow(0)

    /** Images whose tiles the host refused; they show as the original image instead. */
    private val untiled = MutableStateFlow(emptySet<String>())

    private val loaded: StateFlow<MapBackground> = combine(source(), reloads) { source, _ -> source }
        .transformLatest { source ->
            emit(MapBackground.Loading)
            emit(load(source))
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, MapBackground.Loading)

    val background: StateFlow<MapBackground> = combine(loaded, untiled) { background, refused ->
        if (background is MapBackground.Ready && background.image.mediaPath in refused) {
            background.copy(image = MapImage.Original(background.image.mediaPath))
        } else {
            background
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, MapBackground.Loading)

    /** Where the view is, kept across rotation and while the campaign refreshes. */
    val camera = MapCamera()

    fun reload() {
        reloads.value += 1
    }

    /** Shows the original image when the host will not serve this map's tiles, as the website does. */
    fun tilesRefused(mediaPath: String) {
        untiled.value += mediaPath
    }

    /** The world map, or what the campaign says about the location's own map. */
    private fun source() = if (parentId == null) {
        flowOf<Source>(Source.World)
    } else {
        campaign.map { state ->
            val dataset = state.dataset
            when {
                dataset == null -> (state.problem as? CampaignProblem.Unavailable)?.let { Source.Failed(it.failure) } ?: Source.Waiting
                else -> dataset.campaignMap(parentId)?.let { map -> map.localImage?.let(Source::Local) ?: Source.Empty } ?: Source.Missing
            }
        }.distinctUntilChanged()
    }

    private suspend fun load(source: Source): MapBackground {
        val site = sites.site(route.siteId) ?: return MapBackground.Unavailable
        val result = when (source) {
            Source.Waiting -> return MapBackground.Loading
            Source.Missing -> return MapBackground.Unavailable
            Source.Empty -> return MapBackground.NoImage
            is Source.Failed -> return MapBackground.Failed(source.failure)
            Source.World -> maps.world(site)
            is Source.Local -> maps.local(site, source.mediaPath)
        }
        return when (result) {
            is MapLoad.Ready -> MapBackground.Ready(result.image, result.offline)
            MapLoad.NoImage -> MapBackground.NoImage
            is MapLoad.Failed -> MapBackground.Failed(result.failure)
        }
    }

    private sealed interface Source {
        data object Waiting : Source
        data object Missing : Source
        data object Empty : Source
        data class Failed(val failure: HostFailure) : Source
        data object World : Source
        data class Local(val mediaPath: String) : Source
    }
}
