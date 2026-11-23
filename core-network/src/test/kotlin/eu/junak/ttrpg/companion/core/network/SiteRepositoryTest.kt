package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.site.SiteException
import eu.junak.ttrpg.companion.core.network.site.SiteProblem
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

class SiteRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var directory: File
    private lateinit var f: Fixture

    @Before
    fun start() {
        directory = folder.newFolder()
        f = Fixture(directory)
    }

    @After
    fun stop() = f.close()

    private val arrival = event("arrival", 2, """{"name":"Arrival","sitting":1}""")

    private suspend fun problemOf(block: suspend () -> Unit): SiteProblem? =
        (runCatching { block() }.exceptionOrNull() as? SiteException)?.problem

    @Test
    fun signingInSavesTheSiteWithItsRoleAndCampaign() = runTest {
        f.host.campaign = campaign(arrival)

        val site = f.sites.add(f.baseUrl, "dm-secret", allowLocalHttp = true)

        assertEquals(Role.DM, site.role)
        assertEquals("Asurai", site.name)
        assertEquals(listOf(site), f.sites.sites.value)
        assertEquals(campaign(arrival), f.store.readCampaign(site.id)?.json)
        assertTrue(f.cookies.hasSession(f.baseUrl))
    }

    @Test
    fun aWrongPasswordSavesNothing() = runTest {
        assertEquals(SiteProblem.WrongPassword, problemOf { f.sites.add(f.baseUrl, "nope", allowLocalHttp = true) })
        assertTrue(f.sites.sites.value.isEmpty())
    }

    @Test
    fun aMalformedSignInAnswerIsAProblemNotACrash() = runTest {
        f.host.loginAnswer = FakeHost.json("""{"ok":true,"role":"wizard"}""")
        val problem = problemOf { f.sites.add(f.baseUrl, "dm-secret", allowLocalHttp = true) } as SiteProblem.Unreachable
        assertEquals(HostFailure.Reason.MALFORMED, problem.failure.reason)
    }

    @Test
    fun aHostErrorKeepsItsStatus() = runTest {
        f.host.loginAnswer = FakeHost.error(503, "CAMPAIGN_UNAVAILABLE")
        val problem = problemOf { f.sites.add(f.baseUrl, "dm-secret", allowLocalHttp = true) } as SiteProblem.Unreachable
        assertEquals(HostFailure(HostFailure.Reason.HOST_ERROR, "POST /api/login returned 503", 503), problem.failure)
    }

    @Test
    fun signingInAgainChecksTheHostVersion() = runTest {
        val site = f.site()
        f.host.version = "3.1.0"

        assertEquals(SiteProblem.UnsupportedHost("3.1.0"), problemOf { f.sites.signIn(site, "dm-secret") })
        assertFalse(f.host.requests.contains("POST /api/login"))
    }

    @Test
    fun removingASiteStopsLaterCacheWrites() = runTest {
        val site = f.site()
        f.store.writeCampaign(site.id, campaign())

        f.sites.remove(site)
        // A refresh that was already running finishes after the removal.
        f.store.writeCampaign(site.id, campaign(arrival))

        assertNull(f.store.readCampaign(site.id))
        assertFalse(File(directory, "sites/${site.id}").exists())
    }

    @Test
    fun concurrentCacheWritesLeaveOneWholeCopy() {
        val site = f.site()
        val bodies = (1..8).map { writer -> campaign(*Array(writer * 20) { event("e$it", 1, """{"name":"Event $it"}""") }) }
        val failures = CopyOnWriteArrayList<Throwable>()
        bodies.map { body ->
            thread { runCatching { repeat(10) { f.store.writeCampaign(site.id, body) } }.onFailure { failures += it } }
        }.forEach { it.join() }
        assertEquals(emptyList<Throwable>(), failures)
        assertTrue(f.store.readCampaign(site.id)?.json in bodies)
    }
}
