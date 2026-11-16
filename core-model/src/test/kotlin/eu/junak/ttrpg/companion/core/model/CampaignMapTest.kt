package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignMapTest {

    private val map = "/api/media/b_0123456789abcdef0123456789abcdef"
    private val icon1 = "/api/media/b_11111111111111111111111111111111"
    private val icon2 = "/api/media/b_22222222222222222222222222222222"

    private val dataset = CampaignDataset.parse(
        Json.parseToJsonElement(
            """
            {
              "contractVersion": "campaign-data.v1",
              "collections": [
                {"name": "settings", "materialized": true, "revision": 3, "records": [
                  {"key": "pinTypes", "revision": 1, "value": [
                    {"id": "city", "label": "City", "size": 40, "icon": "🏙"},
                    {"id": "camp", "label": "Camp", "defaultIconId": "tower"},
                    {"id": "shop", "label": "Shop", "iconConfig": {"strategy": "random", "files": [{"url": "$icon1"}, {"url": "$icon2"}]}}
                  ]},
                  {"key": "attitudes", "revision": 1, "value": [{"id": "hostile", "label": "Hostile", "bg": "#b00000"}]},
                  {"key": "mapConfigs", "revision": 1, "value": {"world": {"zoomScaleRatio": 0.5}, "local-radov": {"zoomScaleRatio": 7}}}
                ]},
                {"name": "locations", "materialized": true, "revision": 9, "records": [
                  {"key": "radov", "revision": 1, "value": {"name": "Radov", "pinType": "city", "x": 0.25, "y": 0.5,
                    "localMap": "$map", "attitudes": [{"id": "hostile"}, {"id": "gone"}]}},
                  {"key": "camp", "revision": 1, "value": {"name": "Camp", "pinType": "camp", "x": 1.2, "y": -0.1, "size": 99}},
                  {"key": "shop", "revision": 1, "value": {"name": "Shop", "pinType": "shop", "parentId": "radov", "x": 0.1, "y": 0.2, "size": "big"}},
                  {"key": "well", "revision": 1, "value": {"name": "Well", "pinType": "unknown", "parentId": "radov", "x": 0.3, "y": 0.4}},
                  {"key": "unplaced", "revision": 1, "value": {"name": "Unplaced", "x": 0.5}},
                  {"key": "text", "revision": 1, "value": {"name": "Text coordinates", "x": "0.5", "y": "0.5"}},
                  {"key": "fort", "revision": 1, "value": {"name": "Fort", "x": 0.6, "y": 0.6, "visibility": "public", "linkedTwinId": "fort-dm"}},
                  {"key": "fort-dm", "revision": 1, "value": {"name": "Fort (truth)", "x": 0.61, "y": 0.6, "visibility": "dm", "linkedTwinId": "fort"}}
                ]},
                {"name": "events", "materialized": true, "revision": 4, "records": [
                  {"key": "late", "revision": 1, "value": {"name": "Late", "sitting": 2, "order": 1, "locations": ["radov", "camp"]}},
                  {"key": "pinned", "revision": 1, "value": {"name": "Pinned", "sitting": 1, "order": 2, "mapX": 0.7, "mapY": 0.8, "mapParentId": null}},
                  {"key": "first", "revision": 1, "value": {"name": "First", "sitting": 1, "order": 1, "locations": ["radov", "hidden", "shop"]}},
                  {"key": "elsewhere", "revision": 1, "value": {"name": "Elsewhere", "sitting": 3, "mapX": 0.1, "mapY": 0.1, "mapParentId": "radov", "locations": ["camp"]}}
                ]}
              ]
            }
            """.trimIndent(),
        ),
    )

    @Test
    fun theWorldMapPinsPlacedTopLevelLocations() {
        val world = dataset.campaignMap(null)!!
        assertNull(world.parent)
        assertNull(world.localImage)
        assertEquals(listOf("radov", "camp", "fort-dm"), world.pins.map { it.ref.key })
        val radov = world.pins[0]
        assertEquals(0.25 to 0.5, radov.x to radov.y)
        assertEquals(40, radov.size)
        assertEquals("city", radov.iconId)
        assertEquals("🏙", radov.glyph)
        assertEquals(listOf(0xFFB00000), radov.attitudeColors)
        assertTrue(radov.hasLocalMap)
        // Positions outside the image stay; sizes are clamped; a type's bundled icon can be another's.
        val camp = world.pins[1]
        assertEquals(1.2 to -0.1, camp.x to camp.y)
        assertEquals(64, camp.size)
        assertEquals("tower", camp.iconId)
        assertFalse(camp.hasLocalMap)
        assertEquals(0.5, world.markerZoomRatio, 0.0)
    }

    @Test
    fun aLocalMapPinsItsChildrenWithTheWebsitesMarkerRules() {
        val local = dataset.campaignMap("radov")!!
        assertEquals("radov", local.parent?.key)
        assertEquals(map, local.localImage)
        assertEquals(listOf("shop", "well"), local.pins.map { it.ref.key })
        val shop = local.pins[0]
        // A size that is not a number is the default 28; the random marker image follows the website's hash.
        assertEquals(28, shop.size)
        assertEquals(listOf(icon1, icon2)[(fnv1a("shop") % 2u).toInt()], shop.customIcon)
        assertEquals("", shop.iconId)
        val well = local.pins[1]
        assertEquals(28, well.size)
        assertEquals("", well.iconId)
        assertEquals("", well.glyph)
        assertEquals(1.0, local.markerZoomRatio, 0.0)
    }

    @Test
    fun aHiddenOrMissingParentHasNoMap() {
        assertNull(dataset.campaignMap("hidden"))
        assertEquals("radov", dataset.mapParentOf("shop"))
        assertNull(dataset.mapParentOf("radov"))
        assertTrue(dataset.isPlaced("camp"))
        assertFalse(dataset.isPlaced("unplaced"))
    }

    @Test
    fun eventsFollowTheStoryOrderAndTheirPinsOrPlaces() {
        val world = dataset.campaignMap(null)!!
        assertEquals(
            listOf("first" to 0.25, "pinned" to 0.7, "late" to 0.25, "late" to 1.2, "elsewhere" to 1.2),
            world.events.map { it.ref.key to it.x },
        )
        assertEquals(listOf(1, 1, 2, 2, 3), world.events.map { it.sitting })
        val local = dataset.campaignMap("radov")!!
        assertEquals(listOf("first" to 0.1, "elsewhere" to 0.1), local.events.map { it.ref.key to it.x })
    }

    @Test
    fun theTrailSkipsPointsThatDoNotMove() {
        val world = dataset.campaignMap(null)!!
        assertEquals(listOf("first", "pinned", "late", "late"), world.events.trail().map { it.ref.key })
    }

    @Test
    fun eventsAtOneSpotShareAMarker() {
        val stops = dataset.campaignMap(null)!!.events.stops()
        assertEquals(listOf(0.25, 0.7, 1.2), stops.map { it.x })
        assertEquals(listOf(listOf("first", "late"), listOf("pinned"), listOf("late", "elsewhere")), stops.map { stop -> stop.events.map { it.ref.key } })
    }

    @Test
    fun theWebsitesHashPicksTheSameImage() {
        // FNV-1a of "a" is 0xe40c292c.
        assertEquals(0xe40c292cu, fnv1a("a"))
        assertEquals(2166136261u, fnv1a(""))
    }
}
