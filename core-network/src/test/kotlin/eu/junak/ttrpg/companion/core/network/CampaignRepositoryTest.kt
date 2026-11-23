package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.CampaignMutation
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.WriteResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CampaignRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
    }

    @After
    fun stop() = f.close()

    private val arrival = event("arrival", 2, """{"name":"Arrival","sitting":1}""")
    private val rename = CampaignMutation.Put("events", "arrival", 2, JsonObject(mapOf("name" to JsonPrimitive("Arrival at dusk"))))

    @Test
    fun opensTheCachedCopyThenRefreshesIt() = runTest {
        val site = f.site()
        f.store.writeCampaign(site.id, campaign())
        f.host.campaign = campaign(arrival)

        f.campaigns.open(site)

        val state = f.state(site)
        assertNotNull(state.dataset?.record("events", "arrival"))
        assertEquals(null, state.problem)
        assertFalse(state.refreshing)
        assertEquals(campaign(arrival), f.store.readCampaign(site.id)?.json)
    }

    @Test
    fun aFailedRefreshKeepsTheCachedCopy() = runTest {
        val site = f.site()
        f.store.writeCampaign(site.id, campaign(arrival))
        f.host.failing += "/api/campaign"

        f.campaigns.open(site)

        val state = f.state(site)
        assertNotNull(state.dataset?.record("events", "arrival"))
        assertTrue(state.problem is CampaignProblem.Unavailable)
        assertFalse(state.refreshing)
    }

    @Test
    fun aMalformedSessionAnswerIsAProblemNotACrash() = runTest {
        val site = f.site("dm")
        f.host.role = "wizard"

        f.campaigns.refresh(site)

        assertTrue(f.state(site).problem is CampaignProblem.Unavailable)
        assertFalse("refreshing always ends", f.state(site).refreshing)
    }

    @Test
    fun anEndedSessionShowsThePublicCampaign() = runTest {
        val site = f.site("dm")
        f.host.role = null

        f.campaigns.refresh(site)

        assertEquals(CampaignProblem.SessionExpired, f.state(site).problem)
        assertFalse(f.cookies.hasSession(site.baseUrl))
    }

    @Test
    fun anUnsupportedHostIsItsOwnProblem() = runTest {
        val site = f.site()
        f.store.writeCampaign(site.id, campaign(arrival))
        f.host.version = "3.0.0"

        f.campaigns.open(site)

        assertEquals(CampaignProblem.UnsupportedHost("3.0.0"), f.state(site).problem)
        assertNotNull("the cached copy stays", f.state(site).dataset?.record("events", "arrival"))
        assertEquals(listOf("GET /api/version"), f.host.requests)
    }

    @Test
    fun checksTheHostVersionOncePerProcess() = runTest {
        val site = f.site()
        f.campaigns.refresh(site)
        f.campaigns.refresh(site)
        assertEquals(1, f.host.requests.count { it == "GET /api/version" })
    }

    @Test
    fun aCommittedWriteIsSavedEvenWhenTheRefreshFails() = runTest {
        val site = f.site("player")
        f.campaigns.refresh(site)
        // The refresh after the commit reads a session answer it cannot parse.
        f.host.onCommit = { f.host.role = "wizard" }

        assertEquals(WriteResult.Saved, f.campaigns.commit(site, listOf(rename), Role.PLAYER))

        assertTrue(f.state(site).problem is CampaignProblem.Unavailable)
        assertFalse(f.state(site).refreshing)
    }

    @Test
    fun aConflictThatCannotBeRefreshedIsNotRetried() = runTest {
        val site = f.site("player")
        f.campaigns.refresh(site)
        f.host.commitAnswers += FakeHost.error(409, "WRITE_CONFLICT")
        f.host.onCommit = { f.host.failing += "/api/campaign" }

        assertTrue(f.campaigns.commit(site, listOf(rename), Role.PLAYER) is WriteResult.Failed)
    }

    @Test
    fun aRefusedWriteChecksWhetherTheSessionEnded() = runTest {
        val site = f.site("player")
        // The host answers 403, never 401, when the session ended.
        f.host.onCommit = { f.host.role = null }
        assertEquals(WriteResult.SignedOut, f.campaigns.commit(site, listOf(rename), Role.PLAYER))

        f.host.role = "player"
        f.host.onCommit = {}
        f.host.commitAnswers += FakeHost.error(403, "FORBIDDEN")
        assertEquals(WriteResult.Forbidden, f.campaigns.commit(site, listOf(rename), Role.PLAYER))
    }

    @Test
    fun remembersTheRoleTheCampaignWasFetchedFor() = runTest {
        val site = f.site("player")
        f.store.writeCampaign(site.id, campaign(arrival))
        f.host.failing += "/api/campaign"
        f.campaigns.open(site)
        assertEquals("a cached copy's role is not known", null, f.state(site).role)

        f.host.failing.clear()
        f.campaigns.refresh(site)
        assertEquals(Role.PLAYER, f.state(site).role)
    }

    @Test
    fun aChangePreparedForAnotherRoleIsNotSent() = runTest {
        val site = f.site("player")
        f.campaigns.refresh(site)
        // Signed in again as the DM, but that sign-in could not fetch the campaign.
        f.host.role = "dm"

        assertEquals(WriteResult.Conflict, f.campaigns.commit(site, listOf(rename), Role.PLAYER))

        assertTrue(f.host.commits.isEmpty())
        assertEquals("refreshed for the session's role", Role.DM, f.state(site).role)
    }

    @Test
    fun aSignedOutSiteSendsNothing() = runTest {
        val site = f.site()
        assertEquals(WriteResult.SignedOut, f.campaigns.commit(site, listOf(rename), Role.PLAYER))
        assertTrue(f.host.commits.isEmpty())
    }
}
