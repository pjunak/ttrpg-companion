package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.NumberStyles

/*
 * The glance cards that start each sheet tab: boxed values worth a look
 * before the tab's lists. Every box reads as one sentence ("Armor Class 18")
 * so a screen reader does not spell out icon, number and label apart.
 */

/** A tab's glance card: its rows of boxes, then a rule that separates them from the lists. */
@Composable
fun GlanceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val spacing = LocalSpacing.current
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(top = spacing.xs, bottom = spacing.md - spacing.xs),
            verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs),
            content = content,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A row of glance boxes of equal width and height; give each child `Modifier.weight(1f).fillMaxHeight()`. */
@Composable
fun GlanceRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) = Row(
    modifier.fillMaxWidth().height(IntrinsicSize.Min),
    horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm),
    content = content,
)

/** A caption above a row of boxes, such as the casting class when there are several. */
@Composable
fun GlanceCaption(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * A boxed value with its icon, above a short label: Armor 18. [value] may be
 * null for a box that is only an icon and a label. With [onToggle] it is a
 * switch, lit while [checked], such as Inspired.
 */
@Composable
fun GlanceTile(
    icon: ImageVector,
    value: String?,
    label: String,
    description: String,
    modifier: Modifier = Modifier,
    checked: Boolean = false,
    onToggle: ((Boolean) -> Unit)? = null,
    enabled: Boolean = true,
) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val lit = checked && onToggle != null
    val toggle = if (onToggle != null) Modifier.toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle) else Modifier
    val dimmed = onToggle != null && !enabled
    Column(
        modifier
            // A layer that is always there: adding and removing one (Modifier.alpha) as the
            // sheet turned writable left the box undrawn until the next full redraw.
            .graphicsLayer { alpha = if (dimmed) Emphasis.DIMMED else 1f }
            .heightIn(min = spacing.glanceTile)
            .glanceBox(
                MaterialTheme.shapes.medium,
                if (lit) colors.primaryContainer else colors.surfaceContainer,
                if (lit) colors.primary else colors.outlineVariant,
                spacing.hairline,
            )
            .then(toggle)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = spacing.xs + spacing.xxs, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs + spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (lit) colors.onPrimaryContainer else colors.primary, modifier = Modifier.size(spacing.iconMedium))
            if (value != null) Text(value, style = NumberStyles.Medium, color = colors.onSurface, maxLines = 1)
        }
        GlanceLabel(label, if (lit) colors.onPrimaryContainer else colors.onSurfaceVariant, TextAlign.Center)
    }
}

/** A read-only value for [GlanceTiles]. */
data class GlanceValue(val icon: ImageVector, val value: String?, val label: String, val description: String)

/** Read-only tiles in rows of at most [perRow]; the tiles of a row share its width and height. */
@Composable
fun GlanceTiles(values: List<GlanceValue>, perRow: Int) {
    values.chunked(perRow).forEach { row ->
        GlanceRow { row.forEach { GlanceTile(it.icon, it.value, it.label, it.description, Modifier.weight(1f).fillMaxHeight()) } }
    }
}

/** A boxed meter: icon, label and [value] above a bar filled to [fraction] (0..1), such as the carried weight. */
@Composable
fun GlanceMeter(icon: ImageVector, label: String, value: String, fraction: Float, description: String, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = spacing.glanceTile)
            .glanceBox(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant, spacing.hairline)
            .clearAndSetSemantics { contentDescription = description }
            .padding(horizontal = spacing.md - spacing.xs, vertical = spacing.sm),
        verticalArrangement = Arrangement.spacedBy(spacing.sm, Alignment.CenterVertically),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(spacing.iconMedium))
            GlanceLabel(label, colors.onSurfaceVariant, TextAlign.Start, Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, maxLines = 1)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(spacing.meter)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(colors.surfaceContainerHighest),
        ) {
            val filled = fraction.coerceIn(0f, 1f)
            if (filled > 0f) Box(Modifier.fillMaxHeight().weight(filled).background(colors.primary))
            if (filled < 1f) Box(Modifier.fillMaxHeight().weight(1f - filled))
        }
    }
}

/** A small boxed number above a short code, such as a coin; [dimmed] for a value of zero. */
@Composable
fun GlanceCount(value: String, label: String, description: String, modifier: Modifier = Modifier, dimmed: Boolean = false) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .heightIn(min = spacing.control)
            .glanceBox(MaterialTheme.shapes.small, colors.surfaceContainer, colors.outlineVariant, spacing.hairline)
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.xxs, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onSurface,
            maxLines = 1,
            modifier = Modifier.alpha(if (dimmed) Emphasis.DIMMED else 1f),
        )
        GlanceLabel(label, colors.onSurfaceVariant, TextAlign.Center)
    }
}

/** One row of [GlanceFacts]: an icon, a short label and its value. */
data class GlanceFact(val icon: ImageVector, val label: String, val value: String)

/** A boxed list of facts, such as the character's class, species and background. */
@Composable
fun GlanceFacts(facts: List<GlanceFact>, modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .glanceBox(MaterialTheme.shapes.medium, colors.surfaceContainer, colors.outlineVariant, spacing.hairline)
            .padding(horizontal = spacing.md - spacing.xs),
    ) {
        facts.forEachIndexed { index, fact ->
            if (index > 0) HorizontalDivider(color = colors.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = spacing.rowCompact)
                    .padding(vertical = spacing.xs + spacing.xxs)
                    .semantics(mergeDescendants = true) {},
                horizontalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(fact.icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(spacing.iconMedium))
                GlanceLabel(fact.label, colors.onSurfaceVariant, TextAlign.Start, Modifier.width(spacing.glanceLabel))
                Text(fact.value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** The small capitals under or beside a glance value; it shrinks rather than split a word. */
@Composable
private fun GlanceLabel(text: String, color: Color, align: TextAlign, modifier: Modifier = Modifier) =
    WholeWordText(text.uppercase(), modifier, MaterialTheme.typography.labelSmall, color, align, maxLines = 2)

private fun Modifier.glanceBox(shape: Shape, fill: Color, line: Color, width: Dp): Modifier =
    clip(shape).background(fill).border(width, line, shape)
