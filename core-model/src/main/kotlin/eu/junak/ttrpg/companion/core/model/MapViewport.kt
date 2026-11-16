package eu.junak.ttrpg.companion.core.model

import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How an image of [imageWidth]×[imageHeight] native pixels lies in a view of
 * [viewWidth]×[viewHeight] screen pixels: [scale] screen pixels per native
 * pixel, with the image's top-left corner at [offsetX], [offsetY].
 *
 * Map positions are fractions of the image's width and height from its
 * top-left corner, as the host stores them; they may lie outside 0..1.
 */
data class MapViewport(
    val imageWidth: Double,
    val imageHeight: Double,
    val viewWidth: Double,
    val viewHeight: Double,
    val scale: Double,
    val offsetX: Double,
    val offsetY: Double,
    /** Screen pixels per CSS pixel, so zoom levels and limits match the website's. */
    val density: Double = 1.0,
) {
    /** The scale at which the whole image just fits the view. */
    val fitScale: Double get() = min(viewWidth / imageWidth, viewHeight / imageHeight)

    /**
     * The smallest scale: the fitted image, in the website's quarter-level
     * steps and within its −8..2 zoom range.
     */
    val minScale: Double
        get() {
            val level = (floor(log2(fitScale / density) * 4) / 4).coerceIn(MIN_ZOOM, MAX_ZOOM)
            return min(2.0.pow(level) * density, maxScale)
        }

    /** The largest scale: the website's zoom 2, four times native size. */
    val maxScale: Double get() = 2.0.pow(MAX_ZOOM) * density

    /** The website's zoom level for this scale: 0 is native size. */
    val zoom: Double get() = log2(scale / density)

    fun screenX(fractionX: Double): Double = offsetX + fractionX * imageWidth * scale
    fun screenY(fractionY: Double): Double = offsetY + fractionY * imageHeight * scale

    /** The image fitted and centred in the view. */
    fun fitted(): MapViewport = centeredOn(0.5, 0.5, minScale)

    /** Centres the view on a map position at [newScale] (kept within the limits). */
    fun centeredOn(fractionX: Double, fractionY: Double, newScale: Double = scale): MapViewport {
        val target = newScale.coerceIn(minScale, maxScale)
        return copy(
            scale = target,
            offsetX = viewWidth / 2 - fractionX * imageWidth * target,
            offsetY = viewHeight / 2 - fractionY * imageHeight * target,
        ).clamped()
    }

    /**
     * Pans by [panX], [panY] screen pixels and zooms by [zoomFactor] around
     * the screen point [focusX], [focusY], which stays over the same map spot.
     */
    fun transformed(panX: Double, panY: Double, zoomFactor: Double, focusX: Double, focusY: Double): MapViewport {
        val target = (scale * zoomFactor).coerceIn(minScale, maxScale)
        val applied = target / scale
        return copy(
            scale = target,
            offsetX = focusX - (focusX - offsetX) * applied + panX,
            offsetY = focusY - (focusY - offsetY) * applied + panY,
        ).clamped()
    }

    /** The same map spot under the view's centre in a resized view. */
    fun resized(width: Double, height: Double): MapViewport {
        if (width == viewWidth && height == viewHeight) return this
        val centreX = (viewWidth / 2 - offsetX) / (imageWidth * scale)
        val centreY = (viewHeight / 2 - offsetY) / (imageHeight * scale)
        val wasFitted = scale <= minScale
        val next = copy(viewWidth = width, viewHeight = height)
        return if (wasFitted) next.fitted() else next.centeredOn(centreX, centreY)
    }

    /**
     * Keeps the scale within its limits and the view's centre over the image,
     * so a pin at the image's edge can still be brought to the middle.
     */
    fun clamped(): MapViewport {
        val target = scale.coerceIn(minScale, maxScale)
        val width = imageWidth * target
        val height = imageHeight * target
        return copy(
            scale = target,
            offsetX = offsetX.coerceIn(viewWidth / 2 - width, viewWidth / 2),
            offsetY = offsetY.coerceIn(viewHeight / 2 - height, viewHeight / 2),
        )
    }

    /** The part of the image in view, in native pixels (it may extend past the image). */
    fun visibleNativeRect(): NativeRect = NativeRect(
        left = -offsetX / scale,
        top = -offsetY / scale,
        right = (viewWidth - offsetX) / scale,
        bottom = (viewHeight - offsetY) / scale,
    )

    companion object {
        private const val MIN_ZOOM = -8.0
        private const val MAX_ZOOM = 2.0

        /** A view of the whole image, fitted, once the view's size is known. */
        fun fit(imageWidth: Int, imageHeight: Int, viewWidth: Double, viewHeight: Double, density: Double): MapViewport =
            MapViewport(
                imageWidth = max(1, imageWidth).toDouble(),
                imageHeight = max(1, imageHeight).toDouble(),
                viewWidth = max(1.0, viewWidth),
                viewHeight = max(1.0, viewHeight),
                scale = 1.0,
                offsetX = 0.0,
                offsetY = 0.0,
                density = density,
            ).fitted()
    }
}

/**
 * How much a marker grows with zoom: the website's `2^(zoom × ratio)`, where
 * the DM's per-map ratio (0..1) sets how far markers follow the map's scale.
 */
fun markerScale(zoom: Double, ratio: Double): Double =
    if (zoom.isFinite() && ratio.isFinite()) 2.0.pow(zoom * ratio.coerceIn(0.0, 1.0)) else 1.0
