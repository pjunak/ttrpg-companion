package eu.junak.ttrpg.companion.ui.sessions

import androidx.lifecycle.SavedStateHandle
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.network.FakeHost
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.Fixture
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** How the event editor opens, remembers and saves an event today. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class EventEditorViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture
    private lateinit var site: Site

    private val arrival = event("arrival", 2, """{"name":"Arrival","sitting":3,"order":2,"description":"Rain."}""")
    private val renamed = event("arrival", 3, """{"name":"Arrival at dusk","sitting":3,"order":2,"description":"Rain."}""")

    @Before
    fun start() {
        Dispatchers.setMain(StandardTestDispatcher())
        f = Fixture(folder.newFolder())
        f.host.campaign = campaign(arrival)
        site = f.site("player")
    }

    @After
    fun stop() {
        f.close()
        Dispatchers.resetMain()
    }

    private fun handle(vararg route: Pair<String, Any?>) = SavedStateHandle(mapOf("siteId" to site.id, *route))

    /** An editor on [state], once it has opened (or failed to open) its event. */
    private suspend fun TestScope.editor(state: SavedStateHandle): EventEditorViewModel {
        f.campaigns.refresh(site)
        val editor = EventEditorViewModel(state, f.store, f.campaigns, f.writer)
        runCurrent()
        editor.status.first { it != EditorStatus.Loading }
        return editor
    }

    /** Runs [action] and waits until the editor has the host's answer. */
    private suspend fun TestScope.write(editor: EventEditorViewModel, action: () -> Unit): EditorStatus {
        action()
        runCurrent()
        return editor.status.first { it != EditorStatus.Saving }
    }

    /** The single mutation of the [index]th transaction the host received. */
    private fun mutation(index: Int): JsonObject =
        Json.parseToJsonElement(f.host.commits[index]).jsonObject["mutations"]!!.jsonArray.single().jsonObject

    /** A copy of [state]'s contents, as Android restores them after the process was killed. */
    private fun restored(state: SavedStateHandle) = SavedStateHandle(state.keys().associateWith { state.get<Any?>(it) })

    @Test
    fun theDraftAndTheOpeningEventSurviveProcessDeath() = runTest {
        val state = handle("key" to "arrival")
        val first = editor(state)
        val draft = first.fields!!.copy(description = "Rain and fog.")
        first.update(draft)
        // Meanwhile someone renames the event.
        f.host.campaign = campaign(renamed)

        val second = editor(restored(state))

        assertEquals(draft, second.fields)
        assertTrue("the draft still differs from the event as it opened", second.dirty)
        assertEquals(EditorStatus.Done, write(second) { second.save() })
        // Saving merges against the event as it first opened, so the rename made meanwhile stays.
        val value = mutation(0)["value"]!!.jsonObject
        assertEquals("Arrival at dusk", value["name"]?.jsonPrimitive?.content)
        assertEquals("Rain and fog.", value["description"]?.jsonPrimitive?.content)
    }

    @Test
    fun aNewEventIsDirtyOnceItHasContent() = runTest {
        val editor = editor(handle("sitting" to 2))
        assertTrue(editor.creating)
        assertEquals(EventFields(sitting = 2), editor.fields)
        assertFalse(editor.dirty)
        editor.update(EventFields(name = "Ambush", sitting = 2))
        assertTrue(editor.dirty)
        editor.update(EventFields(sitting = 2))
        assertFalse(editor.dirty)
    }

    @Test
    fun anEditedEventIsDirtyWhileItDiffersFromHowItOpened() = runTest {
        val editor = editor(handle("key" to "arrival"))
        val opened = editor.fields!!
        assertFalse(editor.dirty)
        editor.update(opened.copy(name = "Arrival at night"))
        assertTrue(editor.dirty)
        editor.update(opened)
        assertFalse(editor.dirty)
    }

    @Test
    fun aNoteHandedOverFromTheComposerIsDirtyAtOnce() = runTest {
        val editor = editor(handle("sitting" to 3, "note" to "Met the mayor\nHe lied."))
        assertEquals(EventFields(name = "Met the mayor", description = "He lied.", sitting = 3), editor.fields)
        assertTrue("the composer no longer holds the note", editor.dirty)
    }

    @Test
    fun aNewEventKeepsItsKeyWhenSavedAgain() = runTest {
        val editor = editor(handle("sitting" to 3))
        editor.update(EventFields(name = "Ambush", sitting = 3))
        f.host.commitAnswers += FakeHost.error(500, "CAMPAIGN_UNAVAILABLE")
        assertTrue(write(editor) { editor.save() } is EditorStatus.Problem)

        editor.update(EventFields(name = "Ambush at the ford", sitting = 3))
        assertEquals(EditorStatus.Done, write(editor) { editor.save() })

        val key = mutation(0)["key"]?.jsonPrimitive?.content!!
        assertTrue(key.startsWith("ambush-"))
        assertEquals("the key made on the first save is kept", key, mutation(1)["key"]?.jsonPrimitive?.content)
    }

    @Test
    fun aMissingEventIsGone() = runTest {
        val editor = editor(handle("key" to "nowhere"))
        assertEquals(EditorStatus.Problem(SaveProblem.Gone), editor.status.value)
    }

    @Test
    fun anEventChangedSinceOpeningIsNotDeleted() = runTest {
        val editor = editor(handle("key" to "arrival"))
        f.host.campaign = campaign(renamed)
        f.campaigns.refresh(site)

        assertEquals(EditorStatus.Problem(SaveProblem.ChangedBeforeDelete), write(editor) { editor.delete() })
        assertTrue(f.host.commits.isEmpty())
    }

    @Test
    fun aCampaignThatCannotLoadSaysSoAndCanBeRetried() = runTest {
        f.host.failing += "/api/campaign"
        val editor = EventEditorViewModel(handle("key" to "arrival"), f.store, f.campaigns, f.writer)
        runCurrent()
        val failed = editor.status.first { it != EditorStatus.Loading }
        assertTrue("no endless wait for a campaign that will not come", (failed as EditorStatus.CannotLoad).problem is CampaignProblem.Unavailable)

        f.host.failing.clear()
        editor.retry()
        runCurrent()
        assertEquals(EditorStatus.Editing, editor.status.first { it != EditorStatus.Loading })
        assertEquals("Arrival", editor.fields?.name)
    }

    @Test
    fun theFormIsLockedWhileSaving() = runTest {
        val editor = editor(handle("key" to "arrival"))
        val draft = editor.fields!!.copy(description = "Rain and fog.")
        editor.update(draft)
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.host.onCommit = {
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        editor.save()
        runCurrent()
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        editor.update(draft.copy(description = "Typed while saving."))
        assertEquals(draft, editor.fields)
        release.countDown()
        assertEquals(EditorStatus.Done, editor.status.first { it != EditorStatus.Saving })
    }

    @Test
    fun theDmSwitchFollowsTheRoleTheCampaignWasFetchedFor() = runTest {
        // The site was last signed in as the DM, but the session now is a player's.
        f.site("dm")
        f.host.role = "player"
        val asPlayer = editor(handle("key" to "arrival"))
        assertEquals(Role.DM, f.store.site(site.id)?.role)
        assertFalse(asPlayer.canManageVisibility)

        f.host.role = "dm"
        f.campaigns.refresh(site)
        assertTrue(asPlayer.canManageVisibility)
    }

    @Test
    fun leavingAsksOnlyWhenChangesWouldBeLost() = runTest {
        val editor = editor(handle("key" to "arrival"))
        editor.requestLeave()
        assertEquals(Leave.GO, editor.leave.value)

        editor.stay()
        editor.update(editor.fields!!.copy(name = "Arrival at night"))
        editor.requestLeave()
        assertEquals(Leave.CONFIRM_DISCARD, editor.leave.value)
        editor.leaveAnyway()
        assertEquals(Leave.GO, editor.leave.value)
    }

    @Test
    fun leavingWhileSavingWaitsForTheAnswer() = runTest {
        val editor = editor(handle("key" to "arrival"))
        editor.update(editor.fields!!.copy(description = "Rain and fog."))
        f.host.commitAnswers += FakeHost.error(500, "CAMPAIGN_UNAVAILABLE")
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.host.onCommit = {
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        editor.save()
        runCurrent()
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        editor.requestLeave()
        assertEquals(Leave.SAVING, editor.leave.value)
        release.countDown()

        assertTrue(editor.status.first { it != EditorStatus.Saving } is EditorStatus.Problem)
        assertEquals("a failed save keeps the editor open to show why", Leave.STAY, editor.leave.value)
        f.host.onCommit = {}
        editor.requestLeave()
        assertEquals(Leave.CONFIRM_DISCARD, editor.leave.value)
    }
}
