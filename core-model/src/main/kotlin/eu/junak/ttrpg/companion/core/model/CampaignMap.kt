package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * A location's marker on a map, at [x], [y] as fractions of the map image
 * (top-left origin; values outside 0..1 are kept, as on the website).
 */
data class MapPin(
    val ref: RecordRef,
    val x: Double,
    val y: Double,
    /** Marker size in CSS pixels (14..64). */
    val size: Int,
    /** A bundled marker ID the website draws (`city`, `castle`…), or "" for none. */
    val iconId: String,
    /** A marker image the DM uploaded (`/api/media/b_…`), or null. */
    val customIcon: String?,
    /** The marker type's own symbol, or "" for none. */
    val glyph: String,
    /** Colours of the location's attitudes, in its order. */
    val attitudeColors: List<Long>,
    /** Whether the location has its own map to open. */
    val hasLocalMap: Boolean,
)

/** An event on the story trail: its own pin, or one of its places on this map. */
data class MapEventPoint(val ref: RecordRef, val sitting: Int, val x: Double, val y: Double)

/**
 * What one map shows for this session: the world map ([parent] null) or a
 * location's own map. Everything comes from the role-projected campaign, so
 * records the session may not see never get a pin.
 */
data class CampaignMap(
    val parent: RecordRef?,
    /** The local map's image (`/api/media/b_…`); the world map's comes from the host. */
    val localImage: String?,
    val pins: List<MapPin>,
    /** Events in story order (session, then order), with one point per place. */
    val events: List<MapEventPoint>,
    /** The DM's marker zoom ratio for this map (0..1). */
    val markerZoomRatio: Double,
)

/**
 * The map of the location [parentId], or the world map when it is null.
 * Null when that location is not in this session's projection, so a hidden
 * parent shows as unavailable rather than as another image.
 */
fun CampaignDataset.campaignMap(parentId: String?): CampaignMap? {
    val parent = parentId?.let { record(CampaignPage.LOCATIONS.collection, it) ?: return null }
    val definitions = (record("settings", "pinTypes")?.element as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
    val attitudes = settings.attitudes
    val pins = representatives(records(CampaignPage.LOCATIONS.collection)).mapNotNull { record ->
        val value = record.value
        val x = value.number("x")
        val y = value.number("y")
        if (value.text("parentId").ifEmpty { null } != parentId || x == null || y == null) return@mapNotNull null
        val type = value.text("pinType").ifEmpty { "custom" }
        val definition = definitions.firstOrNull { it.text("id") == type }
        val icons = ((definition?.get("iconConfig") as? JsonObject)?.get("files") as? JsonArray).orEmpty().filterIsInstance<JsonObject>()
        val random = (definition?.get("iconConfig") as? JsonObject)?.text("strategy") == "random"
        val chosen = icons.getOrNull(if (random && icons.isNotEmpty()) (fnv1a(record.key) % icons.size.toUInt()).toInt() else 0)
        val bundled = definition?.text("defaultIconId").orEmpty().ifEmpty { type }
        MapPin(
            ref = recordRef(CampaignPage.LOCATIONS, record.key) ?: return@mapNotNull null,
            x = x,
            y = y,
            size = markerSize(listOf(value["size"], definition?.get("size")).firstOrNull { it != null && it !is JsonNull }, type),
            iconId = bundled.takeIf(DEFAULT_MARKER_SIZES::containsKey).orEmpty(),
            customIcon = chosen?.text("url")?.takeIf(::isMediaPath),
            glyph = definition?.text("icon").orEmpty().takeIf { it.length <= MAX_GLYPH_LENGTH }.orEmpty(),
            attitudeColors = (value["attitudes"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.text("id") }
                .mapNotNull { id -> attitudes.firstOrNull { it.id == id }?.color },
            hasLocalMap = isMediaPath(value.text("localMap")),
        )
    }
    val placed = pins.associateBy { it.ref.key }
    val events = records(CampaignPage.EVENTS.collection)
        .sortedWith(compareBy({ it.value.storyNumber("sitting") }, { it.value.storyNumber("order") }))
        .flatMap { record ->
            val value = record.value
            val ref = recordRef(CampaignPage.EVENTS, record.key) ?: return@flatMap emptyList()
            val sitting = value.storyNumber("sitting").toInt()
            val x = value.number("mapX")
            val y = value.number("mapY")
            // An explicit pin wins on its own map; elsewhere the event shows at its places.
            if (x != null && y != null && value.text("mapParentId").ifEmpty { null } == parentId) {
                listOf(MapEventPoint(ref, sitting, x, y))
            } else {
                value.textList("locations").mapNotNull { placed[it] }.map { MapEventPoint(ref, sitting, it.x, it.y) }
            }
        }
    return CampaignMap(
        parent = parent?.let { recordRef(CampaignPage.LOCATIONS, it.key) },
        localImage = parent?.value?.text("localMap")?.takeIf(::isMediaPath),
        pins = pins,
        events = events,
        markerZoomRatio = markerZoomRatio(parentId),
    )
}

/** The map a location's own pin is on: its parent's key, or null for the world map. */
fun CampaignDataset.mapParentOf(locationKey: String): String? =
    record(CampaignPage.LOCATIONS.collection, locationKey)?.value?.text("parentId")?.ifEmpty { null }

/** Whether the location has a position on its map. */
fun CampaignDataset.isPlaced(locationKey: String): Boolean {
    val value = record(CampaignPage.LOCATIONS.collection, locationKey)?.value ?: return false
    return value.number("x") != null && value.number("y") != null
}

/**
 * Points of the story trail: consecutive event points without repeating one
 * that has not moved, as the website joins them.
 */
fun List<MapEventPoint>.trail(): List<MapEventPoint> =
    filterIndexed { index, point -> index == 0 || this[index - 1].let { it.x != point.x || it.y != point.y } }

/** Events that share one spot, such as several events at the same place, shown as one marker. */
data class MapEventStop(val x: Double, val y: Double, val events: List<MapEventPoint>)

/** The trail's markers: one per spot, in story order, each listing its events once. */
fun List<MapEventPoint>.stops(): List<MapEventStop> =
    groupBy { it.x to it.y }.map { (spot, points) -> MapEventStop(spot.first, spot.second, points.distinctBy { it.ref.key }) }

private fun CampaignDataset.markerZoomRatio(parentId: String?): Double {
    val configs = record("settings", "mapConfigs")?.value ?: return 0.0
    val config = configs[if (parentId == null) "world" else "local-$parentId"] as? JsonObject ?: return 0.0
    val ratio = (config["zoomScaleRatio"] as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull ?: return 0.0
    return if (ratio.isFinite()) ratio.coerceIn(0.0, 1.0) else 0.0
}

/**
 * Drops the public half of a DM's reciprocal public/DM twin pair, so a DM
 * sees one marker for it, as the website groups twins.
 */
private fun representatives(records: List<CampaignRecord>): List<CampaignRecord> {
    val byKey = records.associateBy { it.key }
    return records.filter { record ->
        val other = byKey[record.value.text("linkedTwinId")] ?: return@filter true
        val reciprocal = other.key != record.key && other.value.text("linkedTwinId") == record.key &&
            (record.value.text("visibility") == "dm") != (other.value.text("visibility") == "dm")
        !(reciprocal && other.value.text("visibility") == "dm")
    }
}

/** The location's own size, else its type's, else the bundled default; anything that is not a number is 28, as on the website. */
private fun markerSize(size: JsonElement?, type: String): Int {
    val chosen = if (size == null) {
        (DEFAULT_MARKER_SIZES[type] ?: DEFAULT_MARKER_SIZE).toDouble()
    } else {
        (size as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull
    }
    return if (chosen != null && chosen.isFinite()) {
        chosen.coerceIn(MIN_MARKER_SIZE.toDouble(), MAX_MARKER_SIZE.toDouble()).toInt()
    } else {
        DEFAULT_MARKER_SIZE
    }
}

private fun JsonObject.storyNumber(field: String): Double = number(field) ?: 0.0

/** The website's 32-bit FNV-1a hash over UTF-16 code units, which picks a random marker image. */
internal fun fnv1a(value: String): UInt {
    var result = 2166136261u
    for (char in value) {
        result = result xor char.code.toUInt()
        result *= 16777619u
    }
    return result
}

/** The website's bundled markers and their default sizes. */
val DEFAULT_MARKER_SIZES: Map<String, Int> = mapOf(
    "major_city" to 38, "city" to 32, "town" to 28, "village" to 26, "fortress" to 36, "castle" to 36,
    "tower" to 26, "temple" to 28, "shrine" to 26, "tavern" to 24, "market" to 24, "academy" to 30,
    "port" to 30, "bridge" to 24, "camp" to 24, "dungeon" to 28, "cave" to 24, "ruin" to 26,
    "landmark" to 26, "graveyard" to 24, "battlefield" to 28, "forest" to 26, "mountain" to 30,
    "lake" to 28, "curiosity" to 24, "region" to 32, "enemy" to 28, "custom" to 26,
)

private const val DEFAULT_MARKER_SIZE = 28
private const val MIN_MARKER_SIZE = 14
private const val MAX_MARKER_SIZE = 64
private const val MAX_GLYPH_LENGTH = 16
