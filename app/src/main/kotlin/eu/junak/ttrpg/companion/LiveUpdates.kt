package eu.junak.ttrpg.companion

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.events.EventStream
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the open site's campaign current: every (re)connect, campaign change
 * and restore reloads it. A burst of changes during a reload causes one more
 * reload, and a reload that fails is tried again until it works.
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
            .collect { reload(siteId) }
    }

    /**
     * Reloads the campaign, and again after a growing delay while it is
     * unavailable, because the stream may stay quiet for a long time. Stops
     * once it loads, the site is removed or it is no longer followed.
     */
    internal suspend fun reload(siteId: String) {
        var wait = FIRST_RETRY_MS
        while (true) {
            val site = sites.site(siteId) ?: return
            campaigns.refresh(site)
            if (campaigns.state(site).value.problem !is CampaignProblem.Unavailable) return
            delay(wait)
            wait = (wait * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    private companion object {
        const val FIRST_RETRY_MS = 5_000L
        const val MAX_RETRY_MS = 5 * 60_000L
    }
}
