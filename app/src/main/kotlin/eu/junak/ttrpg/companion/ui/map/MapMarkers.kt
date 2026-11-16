package eu.junak.ttrpg.companion.ui.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Castle
import androidx.compose.material.icons.filled.Cottage
import androidx.compose.material.icons.filled.Dangerous
import androidx.compose.material.icons.filled.Festival
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.Fort
import androidx.compose.material.icons.filled.Foundation
import androidx.compose.material.icons.filled.HolidayVillage
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalFlorist
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Signpost
import androidx.compose.material.icons.filled.SportsBar
import androidx.compose.material.icons.filled.Stairs
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TempleBuddhist
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Water
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.MapEventStop
import eu.junak.ttrpg.companion.core.model.MapPin
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/** The smallest area a marker answers to, whatever its drawn size. */
internal val MarkerTouchSize = 40.dp

/**
 * A location's marker: its uploaded image, the website's marker type as an
 * icon, or the type's symbol, ringed in its first attitude's colour. [scale]
 * follows the DM's marker zoom setting; the touch area stays the same.
 */
@Composable
internal fun PinMarker(
    pin: MapPin,
    baseUrl: String,
    focused: Boolean,
    showLabel: Boolean,
    scale: () -> Float,
    onClick: () -> Unit,
) {
    val spacing = LocalSpacing.current
    val ring = optionColor(pin.attitudeColors.firstOrNull(), MaterialTheme.colorScheme.primary)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
        Box(
            Modifier
                .size(maxOf(MarkerTouchSize, pin.size.dp))
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = pin.ref.name },
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(if (focused) spacing.xs else spacing.xxs, ring),
                shadowElevation = spacing.xxs,
                modifier = Modifier
                    .size(pin.size.dp)
                    .graphicsLayer {
                        scaleX = scale()
                        scaleY = scale()
                    },
            ) {
                Box(contentAlignment = Alignment.Center) { PinArtwork(pin, baseUrl) }
            }
        }
        if (showLabel || focused) {
            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                modifier = Modifier.widthIn(max = 160.dp),
            ) {
                Text(
                    pin.ref.name,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun PinArtwork(pin: MapPin, baseUrl: String) {
    val iconSize = (pin.size * ARTWORK_SHARE).dp
    var customFailed by remember(pin.customIcon) { mutableStateOf(false) }
    val custom = pin.customIcon
    when {
        custom != null && !customFailed -> AsyncImage(
            model = baseUrl + custom,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            onError = { customFailed = true },
            modifier = Modifier.size(iconSize),
        )
        pin.iconId.isNotEmpty() -> Icon(markerIcon(pin.iconId), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
        pin.glyph.isNotEmpty() -> Text(pin.glyph, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        else -> Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(iconSize))
    }
}

/**
 * A spot on the story trail, numbered with its first event's session. One
 * event opens at once; several are offered in a menu.
 */
@Composable
internal fun EventStopMarker(stop: MapEventStop, onOpen: (RecordRef) -> Unit) {
    val spacing = LocalSpacing.current
    var choosing by remember { mutableStateOf(false) }
    val first = stop.events.first()
    val description = if (stop.events.size == 1) {
        stringResource(R.string.map_event_marker, first.ref.name)
    } else {
        pluralStringResource(R.plurals.map_event_stop, stop.events.size, stop.events.size)
    }
    Box(
        Modifier
            .size(MarkerTouchSize)
            .clickable(role = Role.Button) { if (stop.events.size == 1) onOpen(first.ref) else choosing = true }
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            border = BorderStroke(spacing.xxs, MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.size(EVENT_MARKER_SIZE.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                val label = if (stop.events.size == 1) first.sitting.takeIf { it > 0 }?.toString().orEmpty() else "${stop.events.size}"
                Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1)
            }
        }
        DropdownMenu(expanded = choosing, onDismissRequest = { choosing = false }) {
            stop.events.forEach { event ->
                DropdownMenuItem(
                    text = {
                        Text(
                            if (event.sitting > 0) stringResource(R.string.map_event_in_session, event.sitting, event.ref.name) else event.ref.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    onClick = {
                        choosing = false
                        onOpen(event.ref)
                    },
                )
            }
        }
    }
}

/** The website's bundled marker types as Material icons; the app does not draw the site's SVG artwork. */
private fun markerIcon(id: String): ImageVector = when (id) {
    "major_city", "city" -> Icons.Filled.LocationCity
    "town" -> Icons.Filled.HolidayVillage
    "village" -> Icons.Filled.Cottage
    "fortress", "tower" -> Icons.Filled.Fort
    "castle" -> Icons.Filled.Castle
    "temple" -> Icons.Filled.AccountBalance
    "shrine" -> Icons.Filled.TempleBuddhist
    "tavern" -> Icons.Filled.SportsBar
    "market" -> Icons.Filled.Storefront
    "academy" -> Icons.Filled.School
    "port" -> Icons.Filled.Anchor
    "bridge" -> Icons.Filled.Signpost
    "camp" -> Icons.Filled.Festival
    "dungeon" -> Icons.Filled.Stairs
    "cave" -> Icons.Filled.Terrain
    "ruin" -> Icons.Filled.Foundation
    "landmark" -> Icons.Filled.Flag
    "graveyard" -> Icons.Filled.LocalFlorist
    "battlefield" -> Icons.Filled.Shield
    "forest" -> Icons.Filled.Forest
    "mountain" -> Icons.Filled.Landscape
    "lake" -> Icons.Filled.Water
    "curiosity" -> Icons.Filled.AutoAwesome
    "region" -> Icons.Filled.Map
    "enemy" -> Icons.Filled.Dangerous
    else -> Icons.Filled.Place
}

private const val ARTWORK_SHARE = 0.62f
internal const val EVENT_MARKER_SIZE = 28
