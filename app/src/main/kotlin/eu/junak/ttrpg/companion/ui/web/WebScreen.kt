package eu.junak.ttrpg.companion.ui.web

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.ui.campaign.CampaignViewModel
import eu.junak.ttrpg.companion.ui.campaign.hostFailureText
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage

/**
 * The site's own web app for pages without a native screen (the Builder, DM
 * tools, add-ons), signed in with the app's session. It shows how far a page
 * has loaded, explains a page that could not load with a way to try again,
 * and steps back through the page's history before leaving.
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
        // The scaffold's padding holds the navigation bar, which the keyboard's inset covers too.
        Box(Modifier.padding(padding).consumeWindowInsets(padding).imePadding().fillMaxSize()) {
            SiteWebView(page, site.baseUrl, site.baseUrl + path, viewModel::webViewCookie, Modifier.fillMaxSize())
            val failure = page.failure
            if (failure != null) {
                Surface(Modifier.fillMaxSize()) {
                    CenteredMessage(stringResource(R.string.web_failed, hostFailureText(failure))) {
                        TextButton(onClick = page::retry) { Text(stringResource(R.string.action_retry)) }
                    }
                }
            } else if (page.progress < 100) {
                LinearProgressIndicator(progress = { page.progress / 100f }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
