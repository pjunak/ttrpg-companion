package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.ui.components.MarkedValue
import eu.junak.ttrpg.companion.ui.components.ToneMarker
import eu.junak.ttrpg.companion.ui.components.glowing
import eu.junak.ttrpg.companion.ui.components.toneColor
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.NumberStyles

/**
 * What changes a value: its name and shown value in its tone, then one row
 * per temporary cause with its marker, name, kind and what it does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MarkCausesSheet(marked: MarkedValue, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val words = markWords()
    val tone = toneColor(marked.mark.tone)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.md).padding(bottom = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Row(
                Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(marked.name, style = MaterialTheme.typography.titleLarge, color = colors.primary, modifier = Modifier.weight(1f))
                ToneMarker(marked.mark.tone)
                Text(marked.shown, style = NumberStyles.Large.glowing(tone))
            }
            Text(stringResource(R.string.mark_causes_title), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            marked.mark.causes.forEach { cause ->
                HorizontalDivider(color = colors.outlineVariant)
                // What a cause does takes at most half the row, so a long effect wraps and a short one leaves the name room.
                BoxWithConstraints {
                    val effectWidth = maxWidth / 2
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = spacing.row).semantics(mergeDescendants = true) {},
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ToneMarker(cause.tone)
                        Column(Modifier.weight(1f)) {
                            Text(causeName(cause, words), style = MaterialTheme.typography.bodyLarge)
                            Text(causeKindName(cause.kind), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        Text(
                            cause.changes.joinToString(", ") { changeText(it, words) },
                            style = MaterialTheme.typography.titleSmall.glowing(toneColor(cause.tone)),
                            textAlign = TextAlign.End,
                            modifier = Modifier.widthIn(max = effectWidth),
                        )
                    }
                }
            }
        }
    }
}
