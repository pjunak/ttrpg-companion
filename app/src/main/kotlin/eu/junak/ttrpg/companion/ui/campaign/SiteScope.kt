package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.core.model.SiteTheme
import eu.junak.ttrpg.companion.ui.theme.AppLanguages
import eu.junak.ttrpg.companion.ui.theme.CompanionTheme
import eu.junak.ttrpg.companion.ui.theme.ProvideLanguage
import eu.junak.ttrpg.companion.ui.theme.deviceLanguage

/**
 * Dresses a site's screens in that site's theme (Classic or Moonlit) and
 * language, as the website does for everyone who opens it.
 */
@Composable
fun SiteScope(viewModel: CampaignViewModel = hiltViewModel(), content: @Composable () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val language = remember(settings) { settings?.languages?.resolve(deviceLanguage(), AppLanguages) }
    CompanionTheme(siteTheme = settings?.theme ?: SiteTheme.CLASSIC) {
        ProvideLanguage(language, content)
    }
}
