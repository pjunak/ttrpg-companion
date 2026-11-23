package eu.junak.ttrpg.companion.ui.web

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.junak.ttrpg.companion.ui.campaign.CampaignViewModel
import eu.junak.ttrpg.companion.ui.components.BackButton

/**
 * The site's own web app for pages without a native screen (the Builder, DM
 * tools, add-ons), signed in with the app's session. Back steps through the
 * page's history before leaving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebScreen(path: String, onBack: () -> Unit, viewModel: CampaignViewModel = hiltViewModel()) {
    val site = viewModel.site.value ?: return
    val page = rememberWebPageState()
    BackHandler(enabled = page.canGoBack, onBack = page::goBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(site.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        SiteWebView(page, site.baseUrl, site.baseUrl + path, viewModel.webViewCookie(), Modifier.padding(padding).fillMaxSize())
    }
}
