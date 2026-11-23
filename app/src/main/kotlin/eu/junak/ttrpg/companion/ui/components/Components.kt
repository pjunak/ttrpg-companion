package eu.junak.ttrpg.companion.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.RecordSummary
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.ReadableWidth

@Composable
fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

/** The dot that marks a way into Settings while an app update waits there. */
@Composable
fun UpdateDot() {
    val description = stringResource(R.string.update_pending)
    Badge(Modifier.semantics { contentDescription = description })
}

/** [content], usually an icon, with an [UpdateDot] in its corner while [pending]. */
@Composable
fun UpdateBadge(pending: Boolean, content: @Composable () -> Unit) {
    BadgedBox(badge = { if (pending) UpdateDot() }) { content() }
}

/**
 * A record's portrait from the host; without one, the record's own [icon]
 * (such as a companion's emoji) or its page icon. [ring] draws the theme's
 * accent around it, as the website frames portraits.
 */
@Composable
fun Portrait(
    baseUrl: String,
    path: String?,
    page: CampaignPage,
    size: Dp,
    icon: String = "",
    ring: Boolean = false,
    shape: Shape = CircleShape,
) {
    val colors = MaterialTheme.colorScheme
    var modifier = Modifier.size(size)
    if (ring) modifier = modifier.border(BorderStroke(size / 48 + LocalSpacing.current.xxs, colors.primary), shape)
    modifier = modifier.clip(shape).background(colors.surfaceContainerHighest)
    if (path == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            if (icon.isNotEmpty()) {
                Text(icon, fontSize = with(LocalDensity.current) { (size * 0.5f).toSp() })
            } else {
                Icon(pageIcon(page), contentDescription = null, tint = colors.primary, modifier = Modifier.size(size * 0.45f))
            }
        }
    } else {
        AsyncImage(
            model = baseUrl + path,
            // Decorative: every portrait sits beside the record's name, which TalkBack reads instead.
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

/** A list row for a record: portrait, name and a short description. */
@Composable
fun RecordRow(
    baseUrl: String,
    summary: RecordSummary,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val secondLine = supporting ?: summary.detailLine
    ListItem(
        headlineContent = { Text(summary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = secondLine.takeIf(String::isNotEmpty)?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Portrait(baseUrl, summary.portrait, summary.page, spacing.portrait, summary.icon) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A compact row for a linked record inside a card. */
@Composable
fun RefRow(baseUrl: String, ref: RecordRef, supporting: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(ref.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = supporting?.takeIf(String::isNotEmpty)?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        leadingContent = { Portrait(baseUrl, ref.portrait, ref.page, LocalSpacing.current.portraitSmall, ref.icon) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

/** A portrait with the name under it, for the party strip. */
@Composable
fun PortraitTile(baseUrl: String, ref: RecordRef, onClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.width(spacing.tileWidth).clip(MaterialTheme.shapes.medium).clickable(onClick = onClick).padding(vertical = spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(spacing.xs),
    ) {
        Portrait(baseUrl, ref.portrait, ref.page, spacing.portraitMedium, ref.icon, ring = true)
        Text(
            ref.name,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A section title in the accent colour with a fine rule, as the website heads its panels. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, trailing: String? = null) {
    val spacing = LocalSpacing.current
    Column(modifier.fillMaxWidth().padding(top = spacing.md, bottom = spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                // A heading for TalkBack, read as written rather than in capitals.
                modifier = Modifier.weight(1f).clearAndSetSemantics {
                    heading()
                    contentDescription = title
                },
            )
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(spacing.xs))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A tonal card that groups related rows. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = LocalSpacing.current.xs), content = content)
    }
}

/** A small filled circle in a status or option colour. */
@Composable
fun StatusDot(color: Color, size: Dp = LocalSpacing.current.sm + LocalSpacing.current.xxs) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

/** A notice above a screen's content, such as an offline or signed-out state. */
@Composable
fun Notice(text: String, action: (@Composable () -> Unit)? = null) {
    val spacing = LocalSpacing.current
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = spacing.md, vertical = spacing.sm),
    ) {
        Column(Modifier.padding(horizontal = spacing.md, vertical = spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text(text, style = MaterialTheme.typography.bodyMedium)
            action?.invoke()
        }
    }
}

/** Caps content at [ReadableWidth], centred, so articles and lists do not stretch across a wide screen. */
fun Modifier.readableWidth(): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = ReadableWidth).fillMaxWidth()

/** A tappable card on the page background, as list entries such as sessions, events and sites show. */
@Composable
fun TappableCard(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
        content = content,
    )
}

/**
 * A screen's main action as an extended floating button. Material's own
 * clears the semantics of its text, so the button carries the [label] itself
 * for TalkBack.
 */
@Composable
fun ActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(label) },
        modifier = Modifier.semantics { contentDescription = label },
    )
}

/** A centred message for empty or loading screens, with an optional [title] and [action] such as "Try again". */
@Composable
fun CenteredMessage(text: String?, loading: Boolean = false, title: String? = null, action: (@Composable () -> Unit)? = null) {
    val spacing = LocalSpacing.current
    Column(
        Modifier.fillMaxSize().padding(spacing.lg),
        verticalArrangement = Arrangement.spacedBy(spacing.md, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) CircularProgressIndicator()
        if (title != null) {
            Text(title, textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        }
        if (text != null) {
            Text(text, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.invoke()
    }
}
