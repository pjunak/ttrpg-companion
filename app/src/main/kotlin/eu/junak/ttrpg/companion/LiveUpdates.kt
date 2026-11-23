package eu.junak.ttrpg.companion

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.events.EventStream
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the open site's campaign current: every (re)connect, campaign change
 * and restore reloads it. Events that arrive during a reload cause one more
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
        follow(siteId, events.events(site.baseUrl))
    }

    internal suspend fun follow(siteId: String, events: Flow<HostEvent>) = coroutineScope {
        // Only "reload now" matters, so any number of events waiting is one signal.
        val signals = Channel<Unit>(Channel.CONFLATED)
        launch {
            events.collect { if (it !is HostEvent.Other) signals.send(Unit) }
            signals.close()
        }
        for (signal in signals) reload(siteId, signals)
    }

    /**
     * Reloads the campaign, and again after a growing delay while it is
     * unavailable, because the stream may stay quiet for a long time. A new
     * event (such as the `hello` of a reconnect once the host is back) ends
     * the wait at once. Stops once the campaign loads, the site is removed or
     * it is no longer followed.
     */
    internal suspend fun reload(siteId: String, signals: ReceiveChannel<Unit>) {
        var wait = FIRST_RETRY_MS
        while (true) {
            val site = sites.site(siteId) ?: return
            campaigns.refresh(site)
            if (campaigns.state(site).value.problem !is CampaignProblem.Unavailable) return
            if (withTimeoutOrNull(wait) { signals.receiveCatching() }?.isClosed == true) return
            wait = (wait * 2).coerceAtMost(MAX_RETRY_MS)
        }
    }

    private companion object {
        const val FIRST_RETRY_MS = 5_000L
        const val MAX_RETRY_MS = 5 * 60_000L
    }
}
