package eu.junak.ttrpg.companion.core.model

/** A rectangle in pixels, relative to the map image's top-left corner at the current zoom. */
data class MapRect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    fun overlaps(other: MapRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom
}

/**
 * The order pin names claim space in: the [focus] pin first (a location
 * opened on the map from its page), then larger markers, then the map's own
 * order. The order never depends on where the view is, so names do not
 * flicker while panning.
 */
fun labelOrder(pins: List<MapPin>, focus: String?): List<Int> =
    pins.indices.sortedWith(compareByDescending<Int> { pins[it].ref.key == focus }.thenByDescending { pins[it].size })

/**
 * Which pin names to show, walking [order]: a name is shown unless it would
 * cover a name already shown or another pin's marker ([markers], at the same
 * indices as [labels]). The [always] name, the focused pin's, is shown
 * regardless. Returns indices into [labels].
 */
fun shownLabels(labels: List<MapRect>, markers: List<MapRect>, order: List<Int>, always: Int? = null): List<Int> {
    val shown = ArrayList<Int>()
    for (index in order) {
        val label = labels[index]
        val free = shown.none { labels[it].overlaps(label) } && markers.indices.none { it != index && markers[it].overlaps(label) }
        if (index == always || free) shown += index
    }
    return shown
}
