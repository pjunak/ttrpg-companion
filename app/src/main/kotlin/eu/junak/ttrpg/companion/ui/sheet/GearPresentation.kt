package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.FeatureSource
import eu.junak.ttrpg.companion.core.model.character.GearItem
import eu.junak.ttrpg.companion.core.model.character.QuickUseItem
import eu.junak.ttrpg.companion.core.model.character.SheetGear
import java.text.NumberFormat
import java.util.Locale

/** The items the character carries (not those equipped or stored), for the Gear tab's carried list. */
fun carriedItems(items: List<GearItem>): List<GearItem> = items.filter { it.location == "carried" }

/** Equipped items that are not in a hand, such as armour: the Gear tab's worn list. */
fun wornItems(gear: SheetGear): List<GearItem> {
    val held = setOfNotNull(gear.mainHand?.itemId, gear.offHand?.itemId)
    return gear.items.filter { it.location == "equipped" && it.id !in held }
}

/** A weight in pounds with at most one decimal, in the reader's number format: "97.6", "270". */
fun formatPounds(pounds: Double, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }.format(pounds)

/** A quick-use item can be used while the rules allow it and some are left. */
fun canUse(item: QuickUseItem): Boolean = item.available && item.quantity > 0

/** Where a feature comes from, as "Paladin 1" or "Oath of Devotion 3"; a species or feat has no level. */
fun featureSourceLabel(source: FeatureSource): String =
    listOfNotNull(source.name ?: source.id ?: source.type.ifEmpty { null }, source.level?.toString()).joinToString(" ")

/** The site's "My character" after switching it [on] or off on character [key]; switching off another's leaves it. */
fun myCharacterAfter(current: String?, key: String, on: Boolean): String? = if (on) key else current.takeUnless { it == key }
