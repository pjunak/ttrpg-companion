package eu.junak.ttrpg.companion.core.network.campaign

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.CampaignMutation
import eu.junak.ttrpg.companion.core.model.HostVersion
import eu.junak.ttrpg.companion.core.network.CodexClient
import eu.junak.ttrpg.companion.core.network.HostException
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the app knows about one site's campaign. [dataset] may come from the
 * cache while [problem] says why it could not be refreshed.
 */
data class CampaignState(
    val dataset: CampaignDataset? = null,
    /** When the shown data was fetched, in epoch milliseconds. */
    val fetchedAt: Long? = null,
    val refreshing: Boolean = false,
    val problem: CampaignProblem? = null,
)

sealed interface CampaignProblem {
    /** The campaign could not be refreshed (offline or a host error); the cached copy is shown. */
    data class Unavailable(val detail: String) : CampaignProblem

    /** The saved session ended; the public campaign is shown until the user signs in again. */
    data object SessionExpired : CampaignProblem

    /** The site now runs a host [version] this app does not support; the cached copy is shown. */
    data class UnsupportedHost(val version: String) : CampaignProblem
}

/** The outcome of a campaign write. */
sealed interface WriteResult {
    data object Saved : WriteResult

    /** The records changed since they were read; the campaign was refreshed so the change can be prepared again. */
    data object Conflict : WriteResult

    /** The site has no signed-in session for this app any more. */
    data object SignedOut : WriteResult

    /** The host refused the change for this role. */
    data object Forbidden : WriteResult

    /** Nothing was saved: no connection or another host error. */
    data class Failed(val detail: String) : WriteResult
}

/** Loads each site's campaign from its cache, then keeps it fresh from the host. */
@Singleton
class CampaignRepository @Inject constructor(
    private val client: CodexClient,
    private val store: SiteStore,
    private val cookies: SiteCookieJar,
) {
    private val states = ConcurrentHashMap<String, MutableStateFlow<CampaignState>>()
    private val locks = ConcurrentHashMap<String, Mutex>()

    /** Sites whose host version this process found supported. */
    private val supported: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun state(site: Site): StateFlow<CampaignState> = flow(site.id).asStateFlow()

    /** Shows the cached campaign at once (if there is one), then refreshes it. */
    suspend fun open(site: Site) {
        val state = flow(site.id)
        if (state.value.dataset == null) {
            val cached = withContext(Dispatchers.IO) { store.readCampaign(site.id) }
            val dataset = cached?.let { runCatching { CodexClient.parseCampaign(it.json) }.getOrNull() }
            if (dataset != null) state.update { it.copy(dataset = dataset, fetchedAt = cached.savedAt) }
        }
        refresh(site)
    }

    /**
     * Fetches the campaign; concurrent refreshes of one site run one after
     * another. The first refresh of a site in this process checks its host
     * version.
     */
    suspend fun refresh(site: Site) {
        reload(site)
    }

    /** [refresh], returning whether the campaign was fetched. */
    private suspend fun reload(site: Site): Boolean {
        val state = flow(site.id)
        return locks.getOrPut(site.id) { Mutex() }.withLock {
            state.update { it.copy(refreshing = true) }
            try {
                val unsupported = unsupportedVersion(site)
                if (unsupported != null) {
                    state.update { it.copy(problem = CampaignProblem.UnsupportedHost(unsupported)) }
                    return@withLock false
                }
                val expired = site.role != null && client.auth(site.baseUrl) == AuthState.Anonymous
                if (expired) cookies.clear(site.baseUrl)
                val (json, dataset) = client.campaign(site.baseUrl)
                withContext(Dispatchers.IO) { store.writeCampaign(site.id, json) }
                state.value = CampaignState(
                    dataset = dataset,
                    fetchedAt = System.currentTimeMillis(),
                    problem = if (expired) CampaignProblem.SessionExpired else null,
                )
                true
            } catch (error: IOException) {
                state.update { it.copy(problem = CampaignProblem.Unavailable(error.message.orEmpty())) }
                false
            } finally {
                state.update { it.copy(refreshing = false) }
            }
        }
    }

    /** The site's host version when this app does not support it, or null. */
    private suspend fun unsupportedVersion(site: Site): String? {
        if (site.id in supported) return null
        val version = client.version(site.baseUrl)
        if (!HostVersion.isSupported(version)) return version
        supported += site.id
        return null
    }

    /**
     * Commits [mutations] as one transaction with the site's current session,
     * then refreshes the campaign so the screens show the saved records and
     * their new revisions. A committed write is [WriteResult.Saved] even when
     * that refresh fails.
     */
    suspend fun commit(site: Site, mutations: List<CampaignMutation>): WriteResult {
        val result = send(site, mutations)
        if (result == WriteResult.Saved || result == WriteResult.Conflict) {
            val refreshed = reload(site)
            // Preparing again on a campaign that could not be refreshed would only conflict again.
            if (result == WriteResult.Conflict && !refreshed) return WriteResult.Failed(problemDetail(site))
        }
        return result
    }

    private suspend fun send(site: Site, mutations: List<CampaignMutation>): WriteResult {
        return try {
            val session = client.auth(site.baseUrl) as? AuthState.SignedIn ?: return WriteResult.SignedOut
            client.commit(site.baseUrl, session.csrfToken, mutations)
            WriteResult.Saved
        } catch (error: HostException) {
            when {
                error.kind == "WRITE_CONFLICT" -> WriteResult.Conflict
                error.status == 401 -> WriteResult.SignedOut
                // The host answers 403 both for a refused change and for a session that ended.
                error.status == 403 -> refusal(site)
                else -> WriteResult.Failed(error.message.orEmpty())
            }
        } catch (error: IOException) {
            WriteResult.Failed(error.message.orEmpty())
        }
    }

    private suspend fun refusal(site: Site): WriteResult = try {
        if (client.auth(site.baseUrl) is AuthState.SignedIn) WriteResult.Forbidden else WriteResult.SignedOut
    } catch (error: IOException) {
        WriteResult.Failed(error.message.orEmpty())
    }

    private fun problemDetail(site: Site): String = when (val problem = flow(site.id).value.problem) {
        is CampaignProblem.Unavailable -> problem.detail
        is CampaignProblem.UnsupportedHost -> "TTRPG Codex ${problem.version}"
        CampaignProblem.SessionExpired, null -> ""
    }

    /**
     * Empties a site's state after sign-in or removal; open screens keep
     * observing the same flow and load again through [open].
     */
    fun forget(siteId: String) {
        states[siteId]?.value = CampaignState()
    }

    private fun flow(siteId: String) = states.getOrPut(siteId) { MutableStateFlow(CampaignState()) }
}
