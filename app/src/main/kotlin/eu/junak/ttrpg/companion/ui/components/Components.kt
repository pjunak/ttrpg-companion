package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.RecordSummary
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

@Composable
fun pageLabel(page: CampaignPage): String = stringResource(
    when (page) {
        CampaignPage.CHARACTERS -> R.string.page_characters
        CampaignPage.LOCATIONS -> R.string.page_locations
        CampaignPage.EVENTS -> R.string.page_events
        CampaignPage.MYSTERIES -> R.string.page_mysteries
        CampaignPage.FACTIONS -> R.string.page_factions
        CampaignPage.PANTHEON -> R.string.page_pantheon
        CampaignPage.ARTIFACTS -> R.string.page_artifacts
        CampaignPage.HISTORY -> R.string.page_history
        CampaignPage.COMPANIONS -> R.string.page_companions
    },
)

/** A record's portrait from the host, or its page icon when it has none. */
@Composable
fun Portrait(baseUrl: String, path: String?, name: String, page: CampaignPage, size: Dp) {
    val modifier = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
    if (path == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(page.icon, style = MaterialTheme.typography.titleLarge)
        }
    } else {
        AsyncImage(
            model = baseUrl + path,
            contentDescription = stringResource(R.string.portrait_description, name),
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
fun RecordRow(baseUrl: String, summary: RecordSummary, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    ListItem(
        headlineContent = { Text(summary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = listOf(summary.subtitle, summary.excerpt).filter(String::isNotEmpty)
            .takeIf { it.isNotEmpty() }
            ?.let { lines -> { Text(lines.joinToString(" · "), maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Portrait(baseUrl, summary.portrait, summary.name, summary.page, spacing.portrait) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A notice above a screen's content, such as an offline or signed-out state. */
@Composable
fun Notice(text: String, action: (@Composable () -> Unit)? = null) {
    val spacing = LocalSpacing.current
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = spacing.md, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action?.invoke()
        }
    }
}

/** A centred message for empty or loading screens. */
@Composable
fun CenteredMessage(text: String?, loading: Boolean = false) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        if (text != null) {
            Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
