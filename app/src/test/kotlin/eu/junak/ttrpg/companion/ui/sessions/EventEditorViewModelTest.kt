package eu.junak.ttrpg.companion.ui.sessions

import androidx.lifecycle.SavedStateHandle
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.network.FakeHost
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.Fixture
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
        val draft = first.fields.value!!.copy(description = "Rain and fog.")
        first.update(draft)
        // Meanwhile someone renames the event.
        f.host.campaign = campaign(renamed)

        val second = editor(restored(state))

        assertEquals(draft, second.fields.value)
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
        assertEquals(EventFields(sitting = 2), editor.fields.value)
        assertFalse(editor.dirty)
        editor.update(EventFields(name = "Ambush", sitting = 2))
        assertTrue(editor.dirty)
        editor.update(EventFields(sitting = 2))
        assertFalse(editor.dirty)
    }

    @Test
    fun anEditedEventIsDirtyWhileItDiffersFromHowItOpened() = runTest {
        val editor = editor(handle("key" to "arrival"))
        val opened = editor.fields.value!!
        assertFalse(editor.dirty)
        editor.update(opened.copy(name = "Arrival at night"))
        assertTrue(editor.dirty)
        editor.update(opened)
        assertFalse(editor.dirty)
    }

    @Test
    fun aNoteHandedOverFromTheComposerIsDirtyAtOnce() = runTest {
        val editor = editor(handle("sitting" to 3, "note" to "Met the mayor\nHe lied."))
        assertEquals(EventFields(name = "Met the mayor", description = "He lied.", sitting = 3), editor.fields.value)
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
}
