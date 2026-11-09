package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignMarkdown
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.summary
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * One record as an article: portrait, name, subtitle and its Markdown fields.
 * Links to other records open natively; other pages of the site open in the
 * web view and other addresses in the browser.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    page: CampaignPage,
    key: String,
    onBack: () -> Unit,
    onOpenRecord: (CampaignPage, String) -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    viewModel: CampaignViewModel = hiltViewModel(),
) {
    val spacing = LocalSpacing.current
    val site by viewModel.site.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val baseUrl = site?.baseUrl.orEmpty()
    val dataset = state.dataset
    val record = dataset?.record(page.collection, key)
    val summary = record?.summary(page)

    Scaffold(
        topBar = {
            TopAppBar(
                // The name heads the article; the bar names the page so it is not shown twice.
                title = { Text(pageLabel(page), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(onClick = { onOpenWebsite(page.recordPath(key)) }) {
                        Icon(Icons.Default.Public, contentDescription = stringResource(R.string.record_open_website))
                    }
                },
            )
        },
    ) { padding ->
        if (dataset == null || record == null || summary == null) {
            Column(Modifier.padding(padding)) {
                CenteredMessage(stringResource(if (dataset == null) R.string.campaign_loading else R.string.record_missing))
            }
            return@Scaffold
        }
        val browser = LocalUriHandler.current
        val links = remember(baseUrl, browser) {
            object : UriHandler {
                override fun openUri(uri: String) {
                    val path = uri.takeIf { it.startsWith("$baseUrl/") }?.removePrefix(baseUrl)
                    when {
                        path == null -> browser.openUri(uri)
                        else -> CampaignPage.parseRecordPath(path)
                            ?.let { (target, targetKey) -> onOpenRecord(target, targetKey) }
                            ?: onOpenWebsite(path)
                    }
                }
            }
        }
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            CampaignStatus(state, onSignIn = {})
            if (summary.portrait != null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Portrait(baseUrl, summary.portrait, summary.name, page, spacing.portraitLarge)
                }
            }
            Text(summary.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            if (summary.subtitle.isNotEmpty()) {
                Text(summary.subtitle, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CompositionLocalProvider(LocalUriHandler provides links) {
                page.articleFields.forEachIndexed { index, field ->
                    val markdown = record.value.text(field)
                    if (markdown.isEmpty()) return@forEachIndexed
                    if (index > 0) {
                        Text(fieldHeading(field), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Markdown(
                        content = remember(markdown, dataset, baseUrl) { CampaignMarkdown.prepare(markdown, dataset, baseUrl) },
                        imageTransformer = Coil3ImageTransformerImpl,
                    )
                }
            }
        }
    }
}

/** `mapNotes` → `Map notes`. */
private fun fieldHeading(field: String): String =
    field.replace(Regex("([a-z])([A-Z])"), "$1 $2").lowercase().replaceFirstChar(Char::uppercase)
