package eu.junak.ttrpg.companion.ui.campaign

import androidx.lifecycle.SavedStateHandle
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.campaign
import eu.junak.ttrpg.companion.core.network.FakeHost.Companion.event
import eu.junak.ttrpg.companion.core.network.Fixture
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.navigation.SITE_ID_ARGUMENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
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

/** How a site screen's view model loads the shared campaign. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CampaignViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()
    private lateinit var f: Fixture

    @Before
    fun start() {
        Dispatchers.setMain(StandardTestDispatcher())
        f = Fixture(folder.newFolder())
        f.host.campaign = campaign(event("arrival", 2, """{"name":"Arrival","sitting":1}"""))
    }

    @After
    fun stop() {
        f.close()
        Dispatchers.resetMain()
    }

    private fun screen(site: Site) = CampaignViewModel(SavedStateHandle(mapOf(SITE_ID_ARGUMENT to site.id)), f.store, f.campaigns, f.cookies)

    /** What [SiteScope] does when the screen is shown without a campaign; waits until [loaded]. */
    private suspend fun TestScope.show(screen: CampaignViewModel, loaded: (CampaignState) -> Boolean) {
        screen.ensureLoaded()
        runCurrent()
        screen.state.first(loaded)
    }

    @Test
    fun aScreenLoadsTheCampaignWhenShownNotWhenMade() = runTest {
        val site = f.site("player")
        val screen = screen(site)
        runCurrent()
        assertTrue("making a screen's view model fetches nothing", f.host.requests.isEmpty())
        assertEquals(site.id, f.store.lastOpenedId)

        show(screen) { it.dataset != null }

        assertEquals("Arrival", screen.state.value.dataset?.record("events", "arrival")?.value?.text("name"))
    }

    @Test
    fun signingInAgainReloadsTheCampaignOnTheScreenBelow() = runTest {
        val site = f.site("player")
        val record = screen(site)
        show(record) { it.role == Role.PLAYER }

        f.sites.signIn(site, "dm-secret")
        assertNull("signing in empties the campaign", record.state.value.dataset)
        show(record) { it.role == Role.DM }

        assertTrue(record.state.value.dataset != null)
    }
}
