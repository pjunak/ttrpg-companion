package eu.junak.ttrpg.companion.ui.sheet

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

private val padRows = listOf(
    listOf(KeypadKey.Digit(1), KeypadKey.Digit(2), KeypadKey.Digit(3)),
    listOf(KeypadKey.Digit(4), KeypadKey.Digit(5), KeypadKey.Digit(6)),
    listOf(KeypadKey.Digit(7), KeypadKey.Digit(8), KeypadKey.Digit(9)),
    listOf(KeypadKey.Clear, KeypadKey.Digit(0), KeypadKey.Delete),
)

/**
 * A bottom sheet with a number pad: an optional [title], the typed [number]
 * with [context] (such as the hit points now) beside it, the keys, then
 * [actions] that use the number. It opens fully: half open would hide the actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NumberPadSheet(
    number: String,
    onNumberChange: (String) -> Unit,
    context: String,
    onDismiss: () -> Unit,
    title: String? = null,
    actions: @Composable RowScope.() -> Unit,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = spacing.md).padding(bottom = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.md - spacing.xs)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, color = colors.primary)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Text(
                    number.ifEmpty { "0" },
                    style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                    color = if (number.isEmpty()) colors.onSurfaceVariant else colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(context, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            padRows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    row.forEach { key -> PadKey(key, Modifier.weight(1f)) { onNumberChange(pressKey(number, key)) } }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), content = actions)
        }
    }
}

@Composable
private fun PadKey(key: KeypadKey, modifier: Modifier, onClick: () -> Unit) = KeySurface(onClick, modifier) {
    when (key) {
        is KeypadKey.Digit -> Text(key.digit.toString(), style = MaterialTheme.typography.headlineSmall)
        KeypadKey.Clear -> {
            val clear = stringResource(R.string.action_clear)
            Text("C", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clearAndSetSemantics { contentDescription = clear })
        }
        KeypadKey.Delete -> Icon(Icons.AutoMirrored.Outlined.Backspace, contentDescription = stringResource(R.string.sheet_keypad_delete))
    }
}

/** A number pad key: a large tonal button around [content]. */
@Composable
internal fun KeySurface(onClick: () -> Unit, modifier: Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = colors.surfaceContainer,
        border = BorderStroke(spacing.hairline, colors.outlineVariant),
        modifier = modifier.height(spacing.controlLarge).alpha(if (enabled) 1f else Emphasis.DIMMED),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** A large action button in its own colours, for the number pad's actions and the like. */
@Composable
internal fun ActionKey(
    @StringRes label: Int,
    container: Color,
    content: Color,
    border: Color,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = container,
        contentColor = content,
        border = BorderStroke(spacing.hairline, border),
        modifier = modifier.height(spacing.controlLarge).alpha(if (enabled) 1f else Emphasis.DIMMED),
    ) {
        Box(contentAlignment = Alignment.Center) { Text(stringResource(label), style = MaterialTheme.typography.titleSmall) }
    }
}
