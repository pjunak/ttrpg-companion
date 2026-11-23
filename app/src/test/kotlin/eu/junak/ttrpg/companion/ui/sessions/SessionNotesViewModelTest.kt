package eu.junak.ttrpg.companion.ui.sessions

import androidx.lifecycle.SavedStateHandle
import eu.junak.ttrpg.companion.core.model.EventProblem
import eu.junak.ttrpg.companion.core.network.FakeHost
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.Fixture
import eu.junak.ttrpg.companion.core.network.campaign.WriteResult
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
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** How the quick note composer of one session saves notes today. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SessionNotesViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        Dispatchers.setMain(StandardTestDispatcher())
        f = Fixture(folder.newFolder())
    }

    @After
    fun stop() {
        f.close()
        Dispatchers.resetMain()
    }

    /** The composer of session 3 on a signed-in site whose campaign is loaded. */
    private suspend fun composer(): SessionNotesViewModel {
        val site = f.site("player")
        f.campaigns.refresh(site)
        return SessionNotesViewModel(SavedStateHandle(mapOf("siteId" to site.id, "number" to 3)), f.store, f.writer)
    }

    /** Sends the note and waits until the composer has the answer. */
    private suspend fun TestScope.send(composer: SessionNotesViewModel) {
        composer.send()
        runCurrent()
        composer.sending.first { !it }
    }

    private fun keyOf(transaction: String): String =
        Json.parseToJsonElement(transaction).jsonObject["mutations"]!!.jsonArray.single().jsonObject["key"]!!.jsonPrimitive.content

    @Test
    fun aRetriedNoteKeepsItsKeySoItIsNeverSavedTwice() = runTest {
        val composer = composer()
        composer.setText("Met the mayor\nHe lied.")
        // The host commits the first attempt, but its answer is lost.
        f.host.onCommit = { body -> f.host.campaign = campaign(event(keyOf(body), 1, """{"name":"Met the mayor","sitting":3}""")) }
        f.host.commitAnswers += FakeHost.error(500, "CAMPAIGN_UNAVAILABLE")
        send(composer)
        assertTrue(composer.problem.value is SaveProblem.NotSaved)

        // The retry reuses the key; the host refuses a second record under it.
        f.host.commitAnswers += FakeHost.error(409, "WRITE_CONFLICT")
        send(composer)

        assertEquals(2, f.host.commits.size)
        assertEquals(keyOf(f.host.commits[0]), keyOf(f.host.commits[1]))
        assertEquals("the retry found the note saved", "", composer.text)
    }

    @Test
    fun aSavedNoteClearsTheComposer() = runTest {
        val composer = composer()
        composer.setText("Met the mayor")
        send(composer)
        assertEquals(1, f.host.commits.size)
        assertEquals("", composer.text)
        assertNull(composer.problem.value)
    }

    @Test
    fun textTypedWhileSavingStays() = runTest {
        val composer = composer()
        composer.setText("Met the mayor")
        val arrived = CountDownLatch(1)
        val release = CountDownLatch(1)
        f.host.onCommit = {
            arrived.countDown()
            release.await(5, TimeUnit.SECONDS)
        }
        composer.send()
        runCurrent()
        assertTrue(arrived.await(5, TimeUnit.SECONDS))

        composer.setText("Met the mayor and his guard")
        release.countDown()
        composer.sending.first { !it }

        assertEquals("Met the mayor and his guard", composer.text)
        assertNull(composer.problem.value)
    }

    @Test
    fun anInvalidNoteSendsNothing() = runTest {
        val composer = composer()
        composer.setText("   ")
        composer.send()
        runCurrent()
        assertEquals(SaveProblem.Invalid(EventProblem.NAME_MISSING), composer.problem.value)
        assertTrue(f.host.commits.isEmpty())
    }

    @Test
    fun aFailedSaveKeepsTheTextAndSaysWhy() = runTest {
        val composer = composer()
        composer.setText("Met the mayor")
        f.host.commitAnswers += FakeHost.error(500, "CAMPAIGN_UNAVAILABLE")
        send(composer)
        val problem = composer.problem.value as SaveProblem.NotSaved
        assertTrue(problem.reason is WriteResult.Failed)
        assertEquals("Met the mayor", composer.text)
    }
}
