package eu.junak.ttrpg.companion.ui.sheet

import androidx.annotation.StringRes
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.ClassLevel
import eu.junak.ttrpg.companion.core.model.character.Coins

/*
 * What the tabs' glance cards show. A box or row appears only for a value
 * the sheet's answer carries; nothing is looked up elsewhere or made up.
 */

/** "6 / 6", for prepared spells or attuned items; null when the sheet does not say both numbers. */
fun countOf(count: Int?, limit: Int?): String? = if (count != null && limit != null) "$count / $limit" else null

/** How full the carried weight meter is, 0..1; null when the sheet lacks the weight or the capacity. */
fun weightShare(carried: Double?, capacity: Double?): Float? = when {
    carried == null || capacity == null -> null
    capacity <= 0.0 -> if (carried > 0.0) 1f else 0f
    else -> (carried / capacity).toFloat().coerceIn(0f, 1f)
}

/** The coins in the order of the strip. */
val COIN_ORDER = listOf("pp", "gp", "ep", "sp", "cp")

/** The coin strip: the five coins, those the sheet lacks at zero, then any other coin the sheet has. */
fun coinStrip(coins: List<Coins>): List<Coins> {
    val amounts = coins.associate { it.coin to it.amount }
    return COIN_ORDER.map { Coins(it, amounts[it] ?: 0) } + coins.filter { it.coin !in COIN_ORDER }
}

/** A coin's full name for a screen reader, or null for one the app does not know. */
@StringRes
fun coinName(coin: String): Int? = when (coin) {
    "pp" -> R.string.coin_platinum
    "gp" -> R.string.coin_gold
    "ep" -> R.string.coin_electrum
    "sp" -> R.string.coin_silver
    "cp" -> R.string.coin_copper
    else -> null
}

/** A special sense's name as the Character Sheets add-on words it, or null for one the app does not know. */
@StringRes
fun senseName(id: String): Int? = when (id) {
    "darkvision" -> R.string.sense_darkvision
    "blindsight" -> R.string.sense_blindsight
    "tremorsense" -> R.string.sense_tremorsense
    "truesight" -> R.string.sense_truesight
    else -> null
}

/** A sense the app has no name for, read from its ID as the website does: `devils-sight` is "Devils sight". */
fun readableId(id: String): String = id.replace('-', ' ').replace('_', ' ').trim().replaceFirstChar { it.uppercaseChar() }

/** A creature size in the reader's language, or null for one the app does not know. */
@StringRes
fun sizeName(size: String): Int? = when (size.lowercase()) {
    "tiny" -> R.string.size_tiny
    "small" -> R.string.size_small
    "medium" -> R.string.size_medium
    "large" -> R.string.size_large
    "huge" -> R.string.size_huge
    "gargantuan" -> R.string.size_gargantuan
    else -> null
}

/** The identity card's class row: each class with its level and subclass, one per line ("Wizard 5 · Evoker"). */
fun classFact(classes: List<ClassLevel>): String? =
    classes.joinToString("\n") { listOfNotNull("${it.name} ${it.level}", it.subclassName ?: it.subclassId?.let(::readableId)).joinToString(" · ") }.ifEmpty { null }

/** The identity card's species row: "Human · Medium", either part alone, or null without both. */
fun speciesFact(species: String?, size: String?): String? = listOfNotNull(species, size).joinToString(" · ").ifEmpty { null }
