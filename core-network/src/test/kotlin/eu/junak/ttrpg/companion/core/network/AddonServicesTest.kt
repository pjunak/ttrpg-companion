package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.error
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.provider
import eu.junak.ttrpg.companion.core.network.services.CharacterService
import eu.junak.ttrpg.companion.core.network.services.ServiceAvailability
import eu.junak.ttrpg.companion.core.network.services.ServiceBinding
import eu.junak.ttrpg.companion.core.network.services.ServiceContract
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import eu.junak.ttrpg.companion.core.network.services.ServiceResult
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AddonServicesTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
    }

    @After
    fun stop() = f.close()

    private val notes = ServiceContract("test.notes", "^1.0.0")
    private val params = buildJsonObject { put("key", JsonPrimitive("kael")) }

    private suspend fun call(site: Site) = f.services.call(site, notes, "load", params)

    /** The service routes the host received, as "connect" or "call". */
    private fun routes() = f.host.serviceRequests.map { it.url.encodedPath.substringAfterLast('/') }

    private fun body(index: Int) = f.host.serviceRequests[index].body?.utf8()

    @Test
    fun connectBindsTheFirstProviderTheHostLists() = runTest {
        f.host.providers[notes.name] = listOf(provider("notes-a"), provider("notes-b", "1.2.0", 'b'))
        val site = f.site("player")

        assertEquals(ServiceResult.Success(ServiceBinding(notes, provider("notes-a"))), f.services.connect(site, notes))
        assertEquals("""{"contractVersion":"client-service-connect.v1","contract":"test.notes","range":"^1.0.0"}""", body(0))
        assertEquals(f.host.csrfToken, f.host.serviceRequests[0].headers["X-Codex-CSRF"])
    }

    @Test
    fun aSiteWithoutProvidersOrClientServicesDoesNotOfferTheFeature() = runTest {
        val site = f.site("player")
        assertEquals(ServiceResult.Failure(ServiceFailure.NotOffered), f.services.connect(site, notes))
        assertEquals(ServiceResult.Failure(ServiceFailure.NotOffered), call(site))
        assertEquals(listOf("connect", "connect"), routes())

        f.host.version = "2.1.0"
        assertEquals(ServiceResult.Failure(ServiceFailure.HostTooOld), f.services.connect(site, notes))
        assertEquals(ServiceResult.Failure(ServiceFailure.HostTooOld), call(site))
    }

    @Test
    fun aCallGoesToTheBoundProviderWithTheSessionsToken() = runTest {
        val bound = provider("notes-a")
        f.host.providers[notes.name] = listOf(bound)
        f.host.serviceResult = """{"name":"Kael"}"""
        val site = f.site("player")

        val result = call(site)
        call(site)

        assertEquals(ServiceResult.Success(HostJson.parseToJsonElement("""{"name":"Kael"}""")), result)
        assertEquals(listOf("connect", "call", "call"), routes())
        assertEquals(
            """{"contractVersion":"client-service-call.v1","contract":"test.notes","providerAddonId":"notes-a",""" +
                """"providerVersion":"4.0.0","providerGeneration":"${bound.generation}","method":"load","params":{"key":"kael"},"deadlineMs":10000}""",
            body(1),
        )
        assertEquals(f.host.csrfToken, f.host.serviceRequests[1].headers["X-Codex-CSRF"])
    }

    @Test
    fun eachRefusalHasItsOwnType() = runTest {
        f.host.providers[notes.name] = listOf(provider("notes-a"))
        val site = f.site("player")
        val answers = listOf(
            error(400, "INVALID_REQUEST", "Name is required.") to ServiceFailure.InvalidRequest("Name is required."),
            error(400, "INVALID_REQUEST") to ServiceFailure.InvalidRequest(null),
            error(403, "FORBIDDEN") to ServiceFailure.Forbidden,
            error(404, "NOT_FOUND") to ServiceFailure.NotFound,
            error(404, "METHOD_NOT_FOUND") to ServiceFailure.MethodNotFound,
            error(409, "CONFLICT") to ServiceFailure.Conflict,
            error(409, "STALE_BINDING") to ServiceFailure.StaleBinding,
            error(429, "RATE_LIMITED") to ServiceFailure.RateLimited,
            error(503, "SERVICE_UNAVAILABLE") to ServiceFailure.ServiceUnavailable,
            error(504, "DEADLINE_EXCEEDED") to ServiceFailure.DeadlineExceeded,
        )
        for ((answer, failure) in answers) {
            f.host.serviceAnswers += answer
            assertEquals(ServiceResult.Failure(failure), call(site))
        }

        f.host.serviceAnswers += error(500, "INTERNAL")
        val other = (call(site) as ServiceResult.Failure).failure as ServiceFailure.Failed
        assertEquals(HostFailure.Reason.HOST_ERROR to 500, other.failure.reason to other.failure.status)
    }

    @Test
    fun aStaleBindingIsNotRepeatedAndTheNextCallConnectsAgain() = runTest {
        f.host.providers[notes.name] = listOf(provider("notes-a"))
        val site = f.site("player")
        call(site)

        // The add-on was updated: same provider, new generation.
        val reloaded = provider("notes-a", mark = 'c')
        f.host.providers[notes.name] = listOf(reloaded)
        assertEquals(ServiceResult.Failure(ServiceFailure.StaleBinding), call(site))
        assertEquals(listOf("connect", "call", "call"), routes())

        assertEquals(ServiceResult.Success(HostJson.parseToJsonElement(f.host.serviceResult)), call(site))
        assertEquals(listOf("connect", "call", "call", "connect", "call"), routes())
        assertEquals(true, body(4)?.contains(reloaded.generation))

        // A stale answer for a provider the host still lists, as after a service it uses changed.
        f.host.serviceAnswers += error(409, "STALE_BINDING")
        assertEquals(ServiceResult.Failure(ServiceFailure.StaleBinding), call(site))
        call(site)
        assertEquals(listOf("connect", "call", "call", "connect", "call", "call", "connect", "call"), routes())
    }

    @Test
    fun anEndedSessionIsSignedOut() = runTest {
        f.host.providers[notes.name] = listOf(provider("notes-a"))
        val site = f.site("player")
        call(site)

        f.host.role = null
        assertEquals(ServiceResult.Failure(ServiceFailure.SignedOut), call(site))
        // The refused call, then the question whether the session ended.
        assertEquals(listOf("POST /api/services/call", "GET /api/auth"), f.host.requests.takeLast(2))

        val reader = f.store.save(Site("s2", f.baseUrl, "Asurai"))
        f.host.serviceRequests.clear()
        assertEquals(ServiceResult.Failure(ServiceFailure.SignedOut), call(reader))
        assertEquals(emptyList<String>(), routes())
    }

    @Test
    fun aNewSessionSendsTheRefusedCallAgainWithItsToken() = runTest {
        f.host.providers[notes.name] = listOf(provider("notes-a"))
        val site = f.site("player")
        call(site)

        val old = f.host.csrfToken
        f.host.csrfToken = "u".repeat(43)
        assertEquals(ServiceResult.Success(HostJson.parseToJsonElement(f.host.serviceResult)), call(site))
        assertEquals(listOf(old, f.host.csrfToken), f.host.serviceRequests.takeLast(2).map { it.headers["X-Codex-CSRF"] })
        assertEquals(listOf("connect", "call", "call", "call"), routes())
    }

    @Test
    fun anUnreachableHostIsOffline() = runTest {
        val down = MockWebServer().apply { start() }
        val url = down.url("/").toString().trimEnd('/')
        down.close()
        val site = Site("s2", url, "Tiamat", "player")

        val failure = (call(site) as ServiceResult.Failure).failure as ServiceFailure.Failed
        assertEquals(HostFailure.Reason.OFFLINE, failure.failure.reason)
    }

    @Test
    fun characterSheetsAreFoundByContract() = runTest {
        val characters = CharacterService(f.services)
        val site = f.site("player")
        f.host.providers["dnd5e.character"] = listOf(provider("any-sheets"))

        assertEquals(
            ServiceAvailability.Available(ServiceBinding(CharacterService.CONTRACT, provider("any-sheets"))),
            characters.availability(site),
        )
        assertEquals("""{"contractVersion":"client-service-connect.v1","contract":"dnd5e.character","range":"^4.0.0"}""", body(0))

        f.host.providers.clear()
        assertEquals(ServiceAvailability.NotOffered, characters.availability(site))

        f.host.version = "2.0.3"
        assertEquals(ServiceAvailability.HostTooOld, characters.availability(site))

        f.server.close()
        val unknown = characters.availability(site) as ServiceAvailability.Unknown
        assertEquals(HostFailure.Reason.OFFLINE, (unknown.failure as ServiceFailure.Failed).failure.reason)
    }
}
