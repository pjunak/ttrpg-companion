package eu.junak.ttrpg.companion.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import eu.junak.ttrpg.companion.core.model.CampaignMap
import eu.junak.ttrpg.companion.core.model.MapPin
import eu.junak.ttrpg.companion.core.model.MapRect
import eu.junak.ttrpg.companion.core.model.MapViewport
import eu.junak.ttrpg.companion.core.model.shownLabels
import eu.junak.ttrpg.companion.core.model.trail
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import kotlin.math.max
import kotlin.math.roundToInt

/** A marker's map position and how far down its own top its touch area's centre lies, in pixels. */
internal data class MarkerSpot(val x: Double, val y: Double, val anchorY: Float)

/**
 * Places each child, horizontally centred, with its touch area's centre on
 * its map spot. Positions are read while placing, so panning and zooming
 * move markers without recomposing them.
 */
@Composable
internal fun MarkerLayer(camera: MapCamera, spots: List<MarkerSpot>, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val viewport = camera.viewport ?: return@layout
            placeables.forEachIndexed { index, placeable ->
                val spot = spots.getOrNull(index) ?: return@forEachIndexed
                val x = viewport.screenX(spot.x).roundToInt() - placeable.width / 2
                val y = (viewport.screenY(spot.y) - spot.anchorY).roundToInt()
                // A marker outside the view stays unplaced, which also keeps it out of TalkBack.
                val visible = x + placeable.width > 0 && y + placeable.height > 0 && x < constraints.maxWidth && y < constraints.maxHeight
                if (visible) placeable.place(x, y)
            }
        }
    }
}

/**
 * Pin names under their markers, drawn over the map's markers. A name is left
 * out where it would cover a name placed earlier in [order] or another pin's
 * marker ([shownLabels]); the [focus] pin's name always shows. Rectangles are
 * measured from the image's corner, so panning keeps the same names; only
 * zooming changes them. A shown name stays inside the view's width. [touch]
 * is a marker's smallest touch area in pixels, as [MarkerSpot] uses it, and
 * [markerScale] the drawn markers' scale.
 */
@Composable
internal fun LabelLayer(camera: MapCamera, pins: List<MapPin>, order: List<Int>, focus: String?, touch: Float, markerScale: () -> Float) {
    val spacing = LocalSpacing.current
    Layout({ pins.forEach { pin -> key(pin.ref.key) { PinLabel(pin.ref.name) } } }, Modifier.fillMaxSize()) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val gap = spacing.xxs.toPx()
        layout(constraints.maxWidth, constraints.maxHeight) {
            val viewport = camera.viewport ?: return@layout
            val scale = markerScale()
            val labels = ArrayList<MapRect>(pins.size)
            val markers = ArrayList<MapRect>(pins.size)
            pins.forEachIndexed { index, pin ->
                val x = pin.x * viewport.imageWidth * viewport.scale
                val y = pin.y * viewport.imageHeight * viewport.scale
                val size = pin.size.dp.toPx()
                val half = size * scale / 2.0
                markers += MapRect(x - half, y - half, x + half, y + half)
                val top = y + max(touch, size) / 2 + gap
                val width = placeables[index].width
                labels += MapRect(x - width / 2.0, top, x + width / 2.0, top + placeables[index].height)
            }
            val always = pins.indexOfFirst { it.ref.key == focus }.takeIf { it >= 0 }
            for (index in shownLabels(labels, markers, order, always)) {
                val placeable = placeables[index]
                val pinX = viewport.screenX(pins[index].x)
                val y = (viewport.offsetY + labels[index].top).roundToInt()
                // Only names of pins in view, so a name never hangs at the edge for a pin outside it.
                if (pinX < 0 || pinX > constraints.maxWidth || y + placeable.height <= 0 || y >= constraints.maxHeight) continue
                val x = (viewport.offsetX + labels[index].left).roundToInt().coerceIn(0, max(0, constraints.maxWidth - placeable.width))
                placeable.place(x, y)
            }
        }
    }
}

internal fun DrawScope.drawTrail(map: CampaignMap, viewport: MapViewport, color: Color, width: Float, dash: FloatArray) {
    val points = map.events.trail()
    if (points.size < 2) return
    val path = Path()
    points.forEachIndexed { index, point ->
        val x = viewport.screenX(point.x).toFloat()
        val y = viewport.screenY(point.y).toFloat()
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color, style = Stroke(width = width, pathEffect = PathEffect.dashPathEffect(dash)))
}
