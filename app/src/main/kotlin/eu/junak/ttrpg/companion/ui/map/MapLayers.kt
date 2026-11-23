package eu.junak.ttrpg.companion.ui.map

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import eu.junak.ttrpg.companion.core.model.CampaignMap
import eu.junak.ttrpg.companion.core.model.MapViewport
import eu.junak.ttrpg.companion.core.model.trail
import kotlinx.coroutines.flow.map
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
