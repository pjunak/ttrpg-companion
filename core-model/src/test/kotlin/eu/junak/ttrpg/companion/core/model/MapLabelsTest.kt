package eu.junak.ttrpg.companion.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which pin names a map shows: none covers another name or another pin, in an order panning never changes. */
class MapLabelsTest {

    private fun pin(key: String, size: Int = 32) =
        MapPin(RecordRef(CampaignPage.LOCATIONS, key, key, null), 0.5, 0.5, size, "", null, "", emptyList(), false)

    private fun rect(left: Double, top: Double, width: Double = 100.0, height: Double = 20.0) =
        MapRect(left, top, left + width, top + height)

    /** Markers far away from every label. */
    private fun apart(count: Int) = List(count) { rect(10_000.0 + it * 100, 10_000.0, 10.0, 10.0) }

    @Test
    fun aNameThatWouldCoverAnEarlierOneIsLeftOut() {
        val labels = listOf(rect(0.0, 0.0), rect(50.0, 10.0), rect(200.0, 0.0), rect(150.0, 15.0))
        // 1 overlaps 0, 3 overlaps 2; 1 and 3 only touch.
        assertEquals(listOf(0, 2), shownLabels(labels, apart(4), listOf(0, 1, 2, 3)))
        assertEquals(listOf(1, 3), shownLabels(labels, apart(4), listOf(1, 0, 3, 2)))
    }

    @Test
    fun aNameThatWouldCoverAnotherPinIsLeftOut() {
        val labels = listOf(rect(0.0, 30.0), rect(300.0, 30.0))
        // Pin 1's marker sits on pin 0's name; each pin's own marker sits above its name.
        val markers = listOf(rect(40.0, 0.0, 20.0, 20.0), rect(60.0, 35.0, 20.0, 20.0))
        assertEquals(listOf(1), shownLabels(labels, markers, listOf(0, 1)))
    }

    @Test
    fun theFocusedNameIsAlwaysShown() {
        val labels = listOf(rect(0.0, 30.0), rect(50.0, 30.0))
        val markers = listOf(rect(40.0, 0.0, 20.0, 20.0), rect(60.0, 35.0, 20.0, 20.0))
        assertEquals(listOf(0), shownLabels(labels, markers, listOf(0, 1), always = 0))
    }

    @Test
    fun theFocusedPinComesFirstThenLargerMarkersThenTheMapsOrder() {
        val pins = listOf(pin("well", 20), pin("radov", 40), pin("shop", 20), pin("fort", 40), pin("mill", 20))
        assertEquals(listOf(1, 3, 0, 2, 4), labelOrder(pins, focus = null))
        assertEquals(listOf(2, 1, 3, 0, 4), labelOrder(pins, focus = "shop"))
    }

    @Test
    fun panningKeepsTheSameNames() {
        val labels = listOf(rect(0.0, 0.0), rect(50.0, 10.0), rect(300.0, 40.0), rect(320.0, 50.0))
        val markers = listOf(rect(30.0, -30.0, 20.0, 20.0), rect(80.0, -20.0, 20.0, 20.0), rect(340.0, 10.0, 20.0, 20.0), rect(360.0, 20.0, 20.0, 20.0))
        fun MapRect.panned() = MapRect(left - 1234.5, top + 678.0, right - 1234.5, bottom + 678.0)
        val order = listOf(3, 2, 1, 0)
        assertEquals(shownLabels(labels, markers, order), shownLabels(labels.map { it.panned() }, markers.map { it.panned() }, order))
    }
}
