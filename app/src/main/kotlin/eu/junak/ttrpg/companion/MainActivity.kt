package eu.junak.ttrpg.companion

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.feature.update.Updater
import eu.junak.ttrpg.companion.feature.update.isPending
import eu.junak.ttrpg.companion.navigation.CompanionNavHost
import eu.junak.ttrpg.companion.ui.theme.CompanionTheme
import eu.junak.ttrpg.companion.ui.theme.LocalThemeMode
import eu.junak.ttrpg.companion.ui.theme.ThemeMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var liveUpdates: LiveUpdates

    @Inject
    lateinit var sites: SiteStore

    @Inject
    lateinit var updater: Updater

    @Inject
    lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Silent: a newer release only shows as a dot on the ways into Settings.
        updater.checkOnLaunch()
        val updatePendingFlow = updater.state.map { it.isPending }.distinctUntilChanged()
        // A fresh start reopens the last site; a recreated activity restores its own screens.
        val initialSiteId = if (savedInstanceState == null) sites.lastOpenedId else null
        setContent {
            val mode by preferences.themeMode.collectAsStateWithLifecycle()
            val updatePending by updatePendingFlow.collectAsStateWithLifecycle(initialValue = false)
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // Status and navigation bar icons follow the app's theme, not only the system's.
            LaunchedEffect(dark) {
                val style = if (dark) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalThemeMode provides mode) {
                CompanionTheme {
                    CompanionNavHost(liveUpdates, initialSiteId, updatePending)
                }
            }
        }
    }
}
