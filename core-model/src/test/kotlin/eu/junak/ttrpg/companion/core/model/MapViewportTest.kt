package eu.junak.ttrpg.companion.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.log2
import kotlin.math.sqrt

class MapViewportTest {

    private val fitted = MapViewport.fit(2000, 1000, 1000.0, 800.0, density = 1.0)

    private fun assertNear(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-9)

    @Test
    fun fitsAndCentresTheImage() {
        assertNear(0.5, fitted.scale)
        assertNear(0.0, fitted.offsetX)
        assertNear(150.0, fitted.offsetY)
        assertNear(250.0, fitted.screenX(0.25))
        assertNear(400.0, fitted.screenY(0.5))
        val visible = fitted.visibleNativeRect()
        assertNear(0.0, visible.left)
        assertNear(-300.0, visible.top)
        assertNear(2000.0, visible.right)
        assertNear(1300.0, visible.bottom)
    }

    @Test
    fun theFitLevelSnapsDownToQuarterZoomsInCssPixels() {
        val view = MapViewport.fit(1000, 1000, 600.0, 600.0, density = 1.0)
        // log2(0.6) = -0.74 becomes zoom -0.75.
        assertNear(-0.75, view.zoom)
        assertTrue(view.scale <= view.fitScale)
        val dense = MapViewport.fit(1000, 1000, 1200.0, 1200.0, density = 2.0)
        assertNear(-0.75, dense.zoom)
        assertNear(view.scale * 2, dense.scale)
        assertNear(8.0, dense.maxScale)
    }

    @Test
    fun zoomKeepsTheSpotUnderTheFingersInPlace() {
        val zoomed = fitted.transformed(panX = 0.0, panY = 0.0, zoomFactor = 2.0, focusX = 500.0, focusY = 400.0)
        assertNear(1.0, zoomed.scale)
        assertNear(-500.0, zoomed.offsetX)
        assertNear(-100.0, zoomed.offsetY)
        assertNear((500 - fitted.offsetX) / fitted.scale, (500 - zoomed.offsetX) / zoomed.scale)
    }

    @Test
    fun zoomAndPanStayWithinTheLimits() {
        assertNear(4.0, fitted.transformed(0.0, 0.0, 100.0, 500.0, 400.0).scale)
        assertNear(0.5, fitted.transformed(0.0, 0.0, 0.01, 500.0, 400.0).scale)
        // The view's centre may reach the image's edge but not leave it.
        val zoomed = fitted.centeredOn(0.5, 0.5, 1.0)
        assertNear(500.0, zoomed.transformed(5000.0, 0.0, 1.0, 0.0, 0.0).offsetX)
        assertNear(-1500.0, zoomed.transformed(-5000.0, 0.0, 1.0, 0.0, 0.0).offsetX)
        assertNear(-1500.0, zoomed.centeredOn(1.2, 0.5).offsetX)
    }

    @Test
    fun resizingKeepsAFittedMapFittedAndAZoomedMapCentred() {
        val larger = fitted.resized(2000.0, 1600.0)
        assertNear(1.0, larger.scale)
        assertNear(300.0, larger.offsetY)
        val zoomed = fitted.centeredOn(0.5, 0.5, 1.0).resized(600.0, 800.0)
        assertNear(1.0, zoomed.scale)
        assertNear(-700.0, zoomed.offsetX)
        assertNear(-100.0, zoomed.offsetY)
    }

    @Test
    fun markersFollowTheDmsZoomRatio() {
        assertNear(1.0, markerScale(-1.0, 0.0))
        assertNear(1 / sqrt(2.0), markerScale(-1.0, 0.5))
        assertNear(0.5, markerScale(-1.0, 3.0))
        assertNear(1.0, markerScale(Double.NaN, 0.5))
        assertNear(log2(0.5), fitted.zoom)
    }
}
