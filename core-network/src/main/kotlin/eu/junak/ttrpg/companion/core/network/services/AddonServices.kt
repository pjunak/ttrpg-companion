package eu.junak.ttrpg.companion.core.network.services

import eu.junak.ttrpg.companion.core.model.AuthState
import eu.junak.ttrpg.companion.core.model.ClientServiceCall
import eu.junak.ttrpg.companion.core.model.ClientServiceProvider
import eu.junak.ttrpg.companion.core.network.CodexClient
import eu.junak.ttrpg.companion.core.network.HostException
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** An add-on service contract and the version range the app understands, such as `dnd5e.character` `^4.0.0`. */
data class ServiceContract(val name: String, val range: String)

/** The provider a site's connection chose for [contract]: the first one the host listed. */
data class ServiceBinding(val contract: ServiceContract, val provider: ClientServiceProvider)

/** Why an add-on service could not be reached or refused a call. */
sealed interface ServiceFailure {
    /** The site has no provider of the contract in range: it does not offer the feature. */
    data object NotOffered : ServiceFailure

    /** The site runs a host older than 2.2.0, which has no client service routes (`405`). */
    data object HostTooOld : ServiceFailure

    /** The site has no signed-in session for this app any more. */
    data object SignedOut : ServiceFailure

    /** The request did not match the contract; [issue] is the provider's one-line reason, when it gave one. */
    data class InvalidRequest(val issue: String?) : ServiceFailure

    /** The provider does not allow this call for the session's role. */
    data object Forbidden : ServiceFailure

    /** What the call names is missing or expired. */
    data object NotFound : ServiceFailure

    /** The provider's contract version has no such method. */
    data object MethodNotFound : ServiceFailure

    /** The data changed since the caller read it. */
    data object Conflict : ServiceFailure

    /**
     * The provider was updated or reloaded, or one of the services it uses
     * changed. The binding is dropped, so the next call connects again; the
     * call is not repeated because the host may answer this after the
     * provider ran it.
     */
    data object StaleBinding : ServiceFailure

    data object RateLimited : ServiceFailure

    data object ServiceUnavailable : ServiceFailure

    data object DeadlineExceeded : ServiceFailure

    /** No connection, a timeout or another host error, as [failure] says. */
    data class Failed(val failure: HostFailure) : ServiceFailure
}

sealed interface ServiceResult<out T> {
    data class Success<T>(val value: T) : ServiceResult<T>

    data class Failure(val failure: ServiceFailure) : ServiceResult<Nothing>
}

/** Whether a signed-in site offers an optional feature's service. */
sealed interface ServiceAvailability {
    data class Available(val binding: ServiceBinding) : ServiceAvailability

    /** No provider in range answers; the feature hides itself. */
    data object NotOffered : ServiceAvailability

    /** The host predates add-on services for clients; the feature hides itself. */
    data object HostTooOld : ServiceAvailability

    /** The site could not be asked (no connection, no session, a host error), as [failure] says. */
    data class Unknown(val failure: ServiceFailure) : ServiceAvailability
}

/**
 * Reaches add-on worker services by contract through the host's client
 * service routes. Each site keeps its session's CSRF token and one binding per
 * contract: a call connects on first use and then goes to the same provider
 * until the host reports the binding stale.
 */
@Singleton
class AddonServices @Inject constructor(private val client: CodexClient) {

    private class Connection {
        @Volatile var session: AuthState.SignedIn? = null
        val bindings = ConcurrentHashMap<ServiceContract, ServiceBinding>()
    }

    private val connections = ConcurrentHashMap<String, Connection>()

    /** The provider [site]'s calls of [contract] go to, if one is bound; asks nothing. */
    fun binding(site: Site, contract: ServiceContract): ServiceBinding? = connections[site.baseUrl]?.bindings?.get(contract)

    /** Asks the host for [contract]'s providers again and keeps the first one for later calls. */
    suspend fun connect(site: Site, contract: ServiceContract): ServiceResult<ServiceBinding> =
        withSession(site) { connection, csrf -> connectNow(site, connection, csrf, contract) }

    /** Whether [site] offers [contract], asking the host each time. */
    suspend fun availability(site: Site, contract: ServiceContract): ServiceAvailability =
        when (val result = connect(site, contract)) {
            is ServiceResult.Success -> ServiceAvailability.Available(result.value)
            is ServiceResult.Failure -> when (result.failure) {
                ServiceFailure.NotOffered -> ServiceAvailability.NotOffered
                ServiceFailure.HostTooOld -> ServiceAvailability.HostTooOld
                else -> ServiceAvailability.Unknown(result.failure)
            }
        }

    /**
     * Calls [method] of [contract] with [params] on the site's bound provider,
     * connecting first when there is none, and returns the provider's result.
     */
    suspend fun call(
        site: Site,
        contract: ServiceContract,
        method: String,
        params: JsonObject,
        deadlineMs: Int = DEFAULT_DEADLINE_MS,
    ): ServiceResult<JsonElement> = withSession(site) { connection, csrf ->
        val binding = connection.bindings[contract]
            ?: when (val connected = connectNow(site, connection, csrf, contract)) {
                is ServiceResult.Success -> connected.value
                is ServiceResult.Failure -> return@withSession connected
            }
        val call = ClientServiceCall(contract.name, binding.provider, method, params, deadlineMs)
        try {
            ServiceResult.Success(client.callService(site.baseUrl, csrf, call))
        } catch (error: HostException) {
            if (error.kind == STALE_BINDING) connection.bindings.remove(contract, binding)
            throw error
        }
    }

    private suspend fun connectNow(site: Site, connection: Connection, csrf: String, contract: ServiceContract): ServiceResult<ServiceBinding> {
        val provider = client.connectService(site.baseUrl, csrf, contract.name, contract.range).providers.firstOrNull()
        if (provider == null) {
            connection.bindings.remove(contract)
            return ServiceResult.Failure(ServiceFailure.NotOffered)
        }
        val binding = ServiceBinding(contract, provider)
        connection.bindings[contract] = binding
        return ServiceResult.Success(binding)
    }

    /**
     * Runs [block] with the site's CSRF token and turns host errors into
     * [ServiceFailure]s. The host answers 403 alike for a session that ended,
     * for a token from an earlier session and for a refused call, so a 403
     * asks the host which it was. A token from an earlier session (the user
     * signed in again) failed before any service saw the request, so it is
     * sent once more with the new one.
     */
    private suspend fun <T> withSession(
        site: Site,
        block: suspend (Connection, String) -> ServiceResult<T>,
    ): ServiceResult<T> {
        val connection = connections.getOrPut(site.baseUrl) { Connection() }
        return try {
            repeat(2) {
                val session = connection.session ?: (client.auth(site.baseUrl) as? AuthState.SignedIn)
                    ?.also { connection.session = it }
                    ?: return ServiceResult.Failure(ServiceFailure.SignedOut)
                try {
                    return block(connection, session.csrfToken)
                } catch (error: HostException) {
                    if (error.status != 403) throw error
                    val current = client.auth(site.baseUrl) as? AuthState.SignedIn
                    connection.session = current
                    if (current == null) return ServiceResult.Failure(ServiceFailure.SignedOut)
                    if (current.csrfToken == session.csrfToken) return ServiceResult.Failure(ServiceFailure.Forbidden)
                }
            }
            ServiceResult.Failure(ServiceFailure.Forbidden)
        } catch (error: IOException) {
            ServiceResult.Failure(failure(error))
        }
    }

    private fun failure(error: IOException): ServiceFailure {
        if (error !is HostException) return ServiceFailure.Failed(HostFailure.of(error))
        if (error.status == 405) return ServiceFailure.HostTooOld
        return when (error.kind) {
            "INVALID_REQUEST" -> ServiceFailure.InvalidRequest(error.issue)
            "NOT_FOUND" -> ServiceFailure.NotFound
            "METHOD_NOT_FOUND" -> ServiceFailure.MethodNotFound
            "CONFLICT" -> ServiceFailure.Conflict
            STALE_BINDING -> ServiceFailure.StaleBinding
            "RATE_LIMITED" -> ServiceFailure.RateLimited
            "SERVICE_UNAVAILABLE" -> ServiceFailure.ServiceUnavailable
            "DEADLINE_EXCEEDED" -> ServiceFailure.DeadlineExceeded
            else -> ServiceFailure.Failed(HostFailure.of(error))
        }
    }

    companion object {
        const val DEFAULT_DEADLINE_MS = 10_000
        private const val STALE_BINDING = "STALE_BINDING"
    }
}
