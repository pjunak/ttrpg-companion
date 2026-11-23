package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/** A labelled number in a small box, such as "AC 18"; read as one phrase. */
@Composable
fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = colors.surfaceContainer,
        border = BorderStroke(spacing.hairline, colors.outlineVariant),
        modifier = modifier.semantics(mergeDescendants = true) {},
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = spacing.control).padding(horizontal = spacing.sm + spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.onSurface)
        }
    }
}

/** A chip that switches something on and off, such as Inspired; it shows the accent while [checked]. */
@Composable
fun ToggleChip(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true, icon: ImageVector? = null) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (checked) colors.primaryContainer else colors.surfaceContainer,
        contentColor = if (checked) colors.onPrimaryContainer else colors.onSurfaceVariant,
        border = BorderStroke(spacing.hairline, if (checked) colors.primary else colors.outlineVariant),
        modifier = Modifier
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .alpha(if (enabled) 1f else Emphasis.DIMMED),
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = spacing.control).padding(horizontal = spacing.sm + spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.size(spacing.iconSmall))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** How a [RemovableChip] looks: a condition in the alert colours, concentration in the accent. */
enum class ChipTone { CONDITION, CONCENTRATION }

/**
 * A pill naming something that can be ended, such as a condition; tapping it
 * ends it. [removeLabel] is what TalkBack reads, such as "Poisoned. End".
 */
@Composable
fun RemovableChip(
    text: String,
    tone: ChipTone,
    removeLabel: String,
    onRemove: () -> Unit,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val (container, content, border) = when (tone) {
        ChipTone.CONDITION -> Triple(colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
        ChipTone.CONCENTRATION -> Triple(colors.primaryContainer, colors.onPrimaryContainer, colors.primary)
    }
    Surface(
        onClick = onRemove,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = container,
        contentColor = content,
        border = BorderStroke(spacing.hairline, border),
        modifier = Modifier
            .alpha(if (enabled) 1f else Emphasis.DIMMED)
            .clearAndSetSemantics { contentDescription = removeLabel },
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = spacing.control).padding(horizontal = spacing.md - spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) Icon(leading, contentDescription = null, modifier = Modifier.size(spacing.iconSmall))
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(spacing.iconSmall))
        }
    }
}

/** A dashed pill that adds something, such as "+ Condition". */
@Composable
fun AddChip(text: String, icon: ImageVector, onClick: () -> Unit, enabled: Boolean = true) {
    val spacing = LocalSpacing.current
    val outline = MaterialTheme.colorScheme.outline
    val dash = with(LocalDensity.current) { spacing.xs.toPx() }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.extraLarge,
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .alpha(if (enabled) 1f else Emphasis.DIMMED)
            .drawBehind {
                val radius = size.height / 2
                drawRoundRect(
                    color = outline,
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(width = spacing.hairline.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash))),
                )
            },
    ) {
        Row(
            Modifier.defaultMinSize(minHeight = spacing.control).padding(horizontal = spacing.md - spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(spacing.iconSmall))
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** How a [Banner] looks: an alert (something is not saved) or information (the rules changed). */
enum class BannerTone { ALERT, INFO }

/** A strip above a screen's content that says what is wrong and offers [actions], such as Retry. */
@Composable
fun Banner(text: String, tone: BannerTone, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Surface(
        color = if (tone == BannerTone.ALERT) colors.tertiaryContainer else colors.primaryContainer,
        contentColor = if (tone == BannerTone.ALERT) colors.onTertiaryContainer else colors.onPrimaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Row(
            Modifier.padding(spacing.md - spacing.xs),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            actions()
        }
    }
}

/** An outlined button in the banner's own colour. */
@Composable
fun BannerButton(text: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = LocalContentColor.current),
        border = BorderStroke(LocalSpacing.current.hairline, LocalContentColor.current),
    ) { Text(text) }
}
