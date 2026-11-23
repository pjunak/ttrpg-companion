package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * A list row over a fine rule: a title with an optional line under it, and a
 * trailing value or action. A row that cannot be used now ([enabled] false)
 * keeps its text readable but dimmed.
 */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val spacing = LocalSpacing.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = spacing.row).padding(vertical = spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.md - spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).alpha(if (enabled) 1f else Emphasis.DIMMED)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A [ListRow] that switches something on and off; the whole row takes the tap. */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
) = ListRow(
    title,
    modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
    subtitle,
    enabled,
) {
    Switch(checked = checked, onCheckedChange = null, enabled = enabled)
}

/**
 * Uses as pips: [left] filled, the rest of [total] empty. With [onGiveBack],
 * each empty pip takes a tap that gives one use back, so a mis-tap can be
 * undone. The caller says how many are left in words; the pips themselves
 * are only read out where they can be tapped.
 */
@Composable
fun PipRow(total: Int, left: Int, onGiveBack: (() -> Unit)?, giveBackLabel: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val color = MaterialTheme.colorScheme.primary
    Row(modifier) {
        repeat(total) { index ->
            val filled = index < left
            val tap = if (!filled && onGiveBack != null) {
                Modifier
                    .clickable(role = Role.Button, onClick = onGiveBack)
                    .semantics { contentDescription = giveBackLabel }
            } else {
                Modifier.clearAndSetSemantics {}
            }
            Box(Modifier.size(spacing.pipTouch).clip(CircleShape).then(tap), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(spacing.pip)
                        .clip(CircleShape)
                        .then(if (filled) Modifier.background(color) else Modifier)
                        .border(spacing.xxs, color, CircleShape),
                )
            }
        }
    }
}
