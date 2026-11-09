package eu.junak.ttrpg.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.feature.update.Updater
import eu.junak.ttrpg.companion.navigation.CompanionNavHost
import eu.junak.ttrpg.companion.ui.theme.CompanionTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var liveUpdates: LiveUpdates

    @Inject
    lateinit var sites: SiteStore

    @Inject
    lateinit var updater: Updater

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        updater.checkOnLaunch()
        // A fresh start reopens the last site; a recreated activity restores its own screens.
        val initialSiteId = if (savedInstanceState == null) sites.lastOpenedId else null
        setContent {
            CompanionTheme {
                CompanionNavHost(liveUpdates, initialSiteId)
            }
        }
    }
}
