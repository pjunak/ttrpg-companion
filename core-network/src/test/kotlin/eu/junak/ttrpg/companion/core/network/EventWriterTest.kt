package eu.junak.ttrpg.companion.core.network

import eu.junak.ttrpg.companion.core.model.EventBase
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.campaign.EventSaveResult
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EventWriterTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        f = Fixture(folder.newFolder())
    }

    @After
    fun stop() = f.close()

    private val opened = event("arrival", 2, """{"name":"Arrival","sitting":3,"order":2,"description":"Rain."}""")
    private val renamed = event("arrival", 3, """{"name":"Arrival at dusk","sitting":3,"order":2,"description":"Rain."}""")

    /** A signed-in site showing [opened], and the editor's start on it. */
    private suspend fun openEditor(): Pair<Site, EventBase> {
        f.host.campaign = campaign(opened)
        val site = f.site("player")
        f.campaigns.refresh(site)
        return site to EventBase.of(f.state(site).dataset!!.record("events", "arrival")!!)
    }

    /** The single mutation of the [index]th transaction the host received. */
    private fun mutation(index: Int): JsonObject =
        Json.parseToJsonElement(f.host.commits[index]).jsonObject["mutations"]!!.jsonArray.single().jsonObject

    @Test
    fun mergesWithAChangeCommittedMeanwhile() = runTest {
        val (site, base) = openEditor()
        // Someone renamed the event just before this save reached the host.
        f.host.campaign = campaign(renamed)
        f.host.commitAnswers += FakeHost.error(409, "WRITE_CONFLICT")

        val result = f.writer.save(site, base, EventFields.of(base.value).copy(description = "Rain and fog."))

        assertEquals(EventSaveResult.Saved, result)
        assertEquals(2, f.host.commits.size)
        val retry = mutation(1)
        assertEquals(3L, retry["expectedRevision"]?.jsonPrimitive?.content?.toLong())
        val value = retry["value"]!!.jsonObject
        assertEquals("Arrival at dusk", value["name"]?.jsonPrimitive?.content)
        assertEquals("Rain and fog.", value["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun aClashOnTheSameFieldAsksTheUser() = runTest {
        val (site, base) = openEditor()
        f.host.campaign = campaign(renamed)
        f.host.commitAnswers += FakeHost.error(409, "WRITE_CONFLICT")

        val result = f.writer.save(site, base, EventFields.of(base.value).copy(name = "Arrival at night"))

        assertEquals(EventSaveResult.Conflict(listOf("name")), result)
        assertEquals(1, f.host.commits.size)
    }

    @Test
    fun deletesTheRevisionTheUserSaw() = runTest {
        val (site, base) = openEditor()

        assertEquals(EventSaveResult.Saved, f.writer.delete(site, base))

        assertEquals("delete", mutation(0)["operation"]?.jsonPrimitive?.content)
        assertEquals(2L, mutation(0)["expectedRevision"]?.jsonPrimitive?.content?.toLong())
    }

    @Test
    fun aDeleteNeverDiscardsAChangeMadeAfterOpening() = runTest {
        val (site, base) = openEditor()
        f.host.campaign = campaign(renamed)
        f.campaigns.refresh(site)

        assertEquals("a live update came in before the user confirmed", EventSaveResult.Changed, f.writer.delete(site, base))
        assertEquals(0, f.host.commits.size)
    }

    @Test
    fun aDeleteRacingAChangeShowsTheChange() = runTest {
        val (site, base) = openEditor()
        f.host.campaign = campaign(renamed)
        f.host.commitAnswers += FakeHost.error(409, "WRITE_CONFLICT")

        assertEquals(EventSaveResult.Changed, f.writer.delete(site, base))
        assertEquals(1, f.host.commits.size)
    }

    @Test
    fun aChangeIsPreparedAgainForTheSessionsRole() = runTest {
        // The DM's view of the event has a DM-only link and private notes the public view lacks.
        f.host.dmCampaign = campaign(
            event("arrival", 2, """{"name":"Arrival","sitting":3,"order":2,"description":"Rain.","characters":["kael","spy"],"notes":"secret"}"""),
        )
        val (site, base) = openEditor()
        // Signed in again as the DM, but that sign-in could not fetch the campaign.
        f.host.role = "dm"

        assertEquals(EventSaveResult.Saved, f.writer.save(site, base, EventFields.of(base.value).copy(description = "Rain and fog.")))

        assertEquals(1, f.host.commits.size)
        val value = mutation(0)["value"]!!.jsonObject
        assertEquals(listOf("kael", "spy"), value["characters"]!!.jsonArray.map { it.jsonPrimitive.content })
        assertEquals("secret", value["notes"]?.jsonPrimitive?.content)
        assertEquals("Rain and fog.", value["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun leavingTheScreenDoesNotCancelASaveOnItsWay() = runBlocking {
        val (site, base) = openEditor()
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.host.onCommit = {
            f.host.campaign = campaign(event("arrival", 3, """{"name":"Arrival","sitting":3,"order":2,"description":"Rain and fog."}"""))
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        val caller = launch(Dispatchers.Default) {
            f.writer.save(site, base, EventFields.of(base.value).copy(description = "Rain and fog."))
        }
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        caller.cancel()
        release.countDown()

        // The write completes and the campaign is refreshed though nobody waits any more.
        withTimeout(5_000) { f.campaigns.state(site).first { it.dataset?.record("events", "arrival")?.revision == 3L } }
        assertEquals(1, f.host.commits.size)
    }

    @Test
    fun aRetriedNoteIsNotSavedTwice() = runTest {
        val (site, _) = openEditor()
        val note = EventBase.new("Met the mayor", "abc123")
        val fields = EventFields.note("Met the mayor", 3)
        // The first answer was lost after the host committed the note.
        f.host.campaign = campaign(opened, event(note.key, 1, """{"name":"Met the mayor","sitting":3}"""))
        f.campaigns.refresh(site)

        assertEquals(EventSaveResult.Saved, f.writer.save(site, note, fields))
        assertEquals(0, f.host.commits.size)
    }
}
