package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonElement
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min

/**
 * A map image's tile pyramid from `GET /api/media/{id}/tiles/v1/manifest`
 * (`map-tiles.v1`). Level [depth] has native pixels; each level below halves
 * them, and level 0 fits the whole image in one 256px cell. Tiles are 257px
 * PNGs: one extra source pixel on the right and bottom hides seams.
 */
data class MapTileManifest(val id: String, val width: Int, val height: Int, val depth: Int) {

    /** Native pixels per pixel of [level]: a power of two. */
    fun nativePerLevelPixel(level: Int): Int = 1 shl (depth - level)

    /** Tiles across and down at [level], as the host checks them. */
    fun columns(level: Int): Int = ceilDiv(width, nativePerLevelPixel(level) * TILE_SIZE)
    fun rows(level: Int): Int = ceilDiv(height, nativePerLevelPixel(level) * TILE_SIZE)

    /**
     * The coarsest level that still gives at least one tile pixel per screen
     * pixel at [scale] (screen pixels per native pixel); deeper zoom scales
     * native tiles up.
     */
    fun levelFor(scale: Double): Int {
        if (!(scale > 0)) return 0
        // A small tolerance keeps a level whose pixels are only a hair too coarse.
        val wanted = depth + ceil(log2(scale) - LEVEL_TOLERANCE).toInt()
        return wanted.coerceIn(0, depth)
    }

    /**
     * The tiles of [level] that cover the native-pixel rectangle
     * [left]..[right] × [top]..[bottom], clipped to the image, row by row.
     */
    fun tilesCovering(level: Int, left: Double, top: Double, right: Double, bottom: Double): List<MapTile> {
        val span = (nativePerLevelPixel(level) * TILE_SIZE).toDouble()
        val firstColumn = max(0, floor(left / span).toInt())
        val lastColumn = min(columns(level) - 1, floor(right / span).toInt())
        val firstRow = max(0, floor(top / span).toInt())
        val lastRow = min(rows(level) - 1, floor(bottom / span).toInt())
        if (firstColumn > lastColumn || firstRow > lastRow) return emptyList()
        return (firstRow..lastRow).flatMap { y -> (firstColumn..lastColumn).map { x -> MapTile(level, x, y) } }
    }

    /** Where [tile] lies in native pixels, including its overlapping 257th pixel. */
    fun nativeBounds(tile: MapTile): NativeRect {
        val step = nativePerLevelPixel(tile.level).toDouble()
        val left = tile.x * TILE_SIZE * step
        val top = tile.y * TILE_SIZE * step
        val size = (TILE_SIZE + 1) * step
        return NativeRect(left, top, left + size, top + size)
    }

    /** The tile's address under the map's opaque media path (`/api/media/b_…`). */
    fun tilePath(mediaPath: String, tile: MapTile): String = "$mediaPath/tiles/v1/${tile.level}/${tile.x}/${tile.y}"

    companion object {
        const val CONTRACT = "map-tiles.v1"
        const val TILE_SIZE = 256
        private const val BOUNDARY = "GET /api/media/{id}/tiles/v1/manifest"
        private const val MAX_DIMENSION = 32_768
        private const val MAX_PIXELS = 32L * 1024 * 1024
        private const val LEVEL_TOLERANCE = 0.05

        /** The manifest path of a map's opaque media path. */
        fun manifestPath(mediaPath: String): String = "$mediaPath/tiles/v1/manifest"

        /** The depth the host builds for an image of this size. */
        fun depthFor(width: Int, height: Int): Int {
            var depth = 0
            var size = TILE_SIZE
            while (size < max(width, height)) {
                size *= 2
                depth++
            }
            return depth
        }

        /**
         * Reads a manifest for the image at [mediaPath], rejecting one for
         * another image or with bounds the host never builds.
         */
        fun parse(value: JsonElement, mediaPath: String): MapTileManifest {
            val body = value.objectOrNull() ?: throw HostFormatException(BOUNDARY, "response must be an object")
            if (body["contractVersion"].stringOrNull() != CONTRACT) throw HostFormatException(BOUNDARY, "contract is unsupported")
            val id = body["id"].stringOrNull()
            if (id == null || !isMediaPath(mediaPath) || mediaPath != "/api/media/$id") {
                throw HostFormatException(BOUNDARY, "pyramid belongs to another image")
            }
            val width = body["width"].longOrNull()
            val height = body["height"].longOrNull()
            val depth = body["depth"].longOrNull()
            return checked(id, width ?: 0, height ?: 0, depth ?: -1)
                ?.takeIf { body["tileSize"].longOrNull() == TILE_SIZE.toLong() }
                ?: throw HostFormatException(BOUNDARY, "pyramid bounds are invalid")
        }

        /** A manifest with bounds the host builds, or null for anything else. */
        fun checked(id: String, width: Long, height: Long, depth: Long): MapTileManifest? {
            if (!isMediaPath("/api/media/$id") || width !in 1..MAX_DIMENSION || height !in 1..MAX_DIMENSION ||
                width * height > MAX_PIXELS || depth != depthFor(width.toInt(), height.toInt()).toLong()
            ) {
                return null
            }
            return MapTileManifest(id, width.toInt(), height.toInt(), depth.toInt())
        }

        /**
         * The opaque media path from a `media-blob.v1` answer, such as
         * `GET /api/media/latest/world-map/main`.
         */
        fun parseMediaPath(value: JsonElement, boundary: String): String {
            val body = value.objectOrNull() ?: throw HostFormatException(boundary, "response must be an object")
            if (body["contractVersion"].stringOrNull() != "media-blob.v1") throw HostFormatException(boundary, "contract is unsupported")
            val url = body["url"].stringOrNull()
            if (url == null || !isMediaPath(url) || url != "/api/media/${body["id"].stringOrNull()}") {
                throw HostFormatException(boundary, "media URL is invalid")
            }
            return url
        }

        private fun ceilDiv(value: Int, divisor: Int): Int = (value + divisor - 1) / divisor
    }
}

/** One tile of a [MapTileManifest] pyramid. */
data class MapTile(val level: Int, val x: Int, val y: Int)

/** A rectangle in an image's native pixels. */
data class NativeRect(val left: Double, val top: Double, val right: Double, val bottom: Double)
