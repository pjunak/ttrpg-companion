package eu.junak.ttrpg.companion

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.events.EventStream
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the open site's campaign current: every (re)connect and every campaign
 * change reloads it. A burst of changes during a reload causes one more reload.
 */
@Singleton
class LiveUpdates @Inject constructor(
    private val sites: SiteStore,
    private val campaigns: CampaignRepository,
    private val events: EventStream,
) {
    suspend fun follow(siteId: String) {
        val site = sites.site(siteId) ?: return
        events.events(site.baseUrl)
            .filter { it !is HostEvent.Other }
            .conflate()
            .collect { sites.site(siteId)?.let { current -> campaigns.refresh(current) } }
    }
}
