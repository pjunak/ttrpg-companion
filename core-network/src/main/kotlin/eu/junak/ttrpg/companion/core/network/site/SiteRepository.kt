package eu.junak.ttrpg.companion.core.network.site

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.HostVersion
import eu.junak.ttrpg.companion.core.model.SiteAddress
import eu.junak.ttrpg.companion.core.model.SiteAddressResult
import eu.junak.ttrpg.companion.core.network.CodexClient
import eu.junak.ttrpg.companion.core.network.HostException
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.session.SiteCookieJar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Why adding or signing in to a site failed, for the UI to explain. */
sealed interface SiteProblem {
    data class Address(val reason: SiteAddressResult.Reason) : SiteProblem
    data class UnsupportedHost(val version: String) : SiteProblem
    data object WrongPassword : SiteProblem
    data object TooManyAttempts : SiteProblem
    data class Unreachable(val failure: HostFailure) : SiteProblem
}

class SiteException(val problem: SiteProblem) : Exception(problem.toString())

/** Adds, signs in to and removes campaign sites. */
@Singleton
class SiteRepository @Inject constructor(
    private val client: CodexClient,
    private val store: SiteStore,
    private val cookies: SiteCookieJar,
    private val campaigns: CampaignRepository,
) {
    val sites = store.sites

    /**
     * Checks the address and host version, signs in when a [password] is given
     * (an empty one reads the public campaign) and saves the site with its
     * campaign cached.
     */
    suspend fun add(input: String, password: String, allowLocalHttp: Boolean): Site {
        val baseUrl = when (val address = SiteAddress.normalize(input, allowLocalHttp)) {
            is SiteAddressResult.Valid -> address.baseUrl
            is SiteAddressResult.Invalid -> throw SiteException(SiteProblem.Address(address.reason))
        }
        return signIn(baseUrl, password, existingId = null)
    }

    /** Signs in to a saved site again, for example after its session expired. */
    suspend fun signIn(site: Site, password: String): Site = signIn(site.baseUrl, password, site.id)

    /** Ends the session on the host when possible, then forgets the site and its cache. */
    suspend fun remove(site: Site) {
        if (cookies.hasSession(site.baseUrl)) {
            // An unreachable host must not keep a site on the phone; its session
            // simply expires there.
            runCatching { client.logout(site.baseUrl) }
        }
        cookies.clear(site.baseUrl)
        store.remove(site.id)
        campaigns.forget(site.id)
    }

    private suspend fun signIn(baseUrl: String, password: String, existingId: String?): Site {
        // The host may have been updated since the site was added.
        val version = hostCall { client.version(baseUrl) }
        if (!HostVersion.isSupported(version)) throw SiteException(SiteProblem.UnsupportedHost(version))
        val auth = if (password.isEmpty()) {
            if (cookies.hasSession(baseUrl)) hostCall { client.logout(baseUrl) }
            cookies.clear(baseUrl)
            AuthState.Anonymous
        } else {
            try {
                client.login(baseUrl, password)
            } catch (error: HostException) {
                throw SiteException(
                    when (error.status) {
                        401, 403 -> SiteProblem.WrongPassword
                        429 -> SiteProblem.TooManyAttempts
                        else -> SiteProblem.Unreachable(HostFailure.of(error))
                    },
                )
            } catch (error: IOException) {
                throw SiteException(SiteProblem.Unreachable(HostFailure.of(error)))
            }
        }
        val (json, dataset) = hostCall { client.campaign(baseUrl) }
        val role = (auth as? AuthState.SignedIn)?.role
        val site = withContext(Dispatchers.IO) {
            store.save(
                Site(
                    id = existingId ?: UUID.randomUUID().toString(),
                    baseUrl = baseUrl,
                    name = dataset.name.ifEmpty { baseUrl.substringAfter("://") },
                    roleWire = role?.wire,
                ),
            ).also { hostCall { store.writeCampaign(it.id, json) } }
        }
        // The new role sees a different projection; reload it from the cache.
        campaigns.forget(site.id)
        return site
    }

    private suspend fun <T> hostCall(block: suspend () -> T): T = try {
        block()
    } catch (error: IOException) {
        throw SiteException(SiteProblem.Unreachable(HostFailure.of(error)))
    }
}
