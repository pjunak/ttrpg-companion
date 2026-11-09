package eu.junak.ttrpg.companion.ui.campaign

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.navigation.SITE_ID_ARGUMENT
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared by every screen of one site: the saved site and its campaign. The
 * first screen to open a site loads the cached copy and refreshes it.
 */
@HiltViewModel
class CampaignViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sites: SiteStore,
    private val campaigns: CampaignRepository,
    private val cookies: SiteCookieJar,
) : ViewModel() {

    val siteId: String = checkNotNull(savedStateHandle[SITE_ID_ARGUMENT])

    val site: StateFlow<Site?> = sites.sites
        .map { list -> list.firstOrNull { it.id == siteId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, sites.site(siteId))

    val state: StateFlow<CampaignState> =
        sites.site(siteId)?.let(campaigns::state) ?: MutableStateFlow(CampaignState())

    init {
        sites.lastOpenedId = siteId
        ensureLoaded()
    }

    /** Loads the campaign when nothing is shown, for example after signing in again. */
    fun ensureLoaded() {
        val site = sites.site(siteId) ?: return
        val current = campaigns.state(site).value
        if (current.dataset == null && !current.refreshing) viewModelScope.launch { campaigns.open(site) }
    }

    fun refresh() {
        val site = sites.site(siteId) ?: return
        viewModelScope.launch { campaigns.refresh(site) }
    }

    /** The cookie that signs the in-app web view in to this site, if signed in. */
    fun webViewCookie(): String? = sites.site(siteId)?.let { cookies.webViewCookie(it.baseUrl) }
}
