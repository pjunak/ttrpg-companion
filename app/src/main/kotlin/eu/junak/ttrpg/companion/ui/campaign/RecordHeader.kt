package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import eu.junak.ttrpg.companion.core.model.FactKind
import eu.junak.ttrpg.companion.core.model.RecordDetails
import eu.junak.ttrpg.companion.core.model.details
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/** Portrait, name, subtitle and the status and attitude badges. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Hero(baseUrl: String, details: RecordDetails) {
    val spacing = LocalSpacing.current
    val summary = details.summary
    Column(
        Modifier.fillMaxWidth().padding(top = spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.sm),
    ) {
        val size = if (summary.portrait != null) spacing.portraitLarge else spacing.heroSymbol
        Portrait(baseUrl, summary.portrait, summary.page, size, summary.icon, ring = true)
        Text(
            summary.name,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        if (summary.subtitle.isNotEmpty()) {
            Text(
                summary.subtitle,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        val status = details.facts.firstOrNull { it.kind == FactKind.STATUS }
        if (status != null || details.attitudes.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterHorizontally)) {
                status?.let { Badge(it.text, it.color, it.icon) }
                details.attitudes.forEach { Badge(it.label, it.color, "") }
            }
        }
    }
}

/** A pill with an option's colour, as the website shows statuses and attitudes. */
@Composable
private fun Badge(label: String, color: Long?, icon: String) {
    val spacing = LocalSpacing.current
    val tint = optionColor(color, MaterialTheme.colorScheme.primary)
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(spacing.hairline, tint.copy(alpha = Emphasis.BADGE_OUTLINE)),
    ) {
        Row(
            Modifier.padding(horizontal = spacing.md - spacing.xs, vertical = spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
        ) {
            if (icon.isEmpty()) StatusDot(tint) else Text(icon, color = tint, style = MaterialTheme.typography.labelLarge)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
