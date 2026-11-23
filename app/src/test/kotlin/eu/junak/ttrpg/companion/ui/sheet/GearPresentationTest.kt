package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.FeatureSource
import eu.junak.ttrpg.companion.core.model.character.GearItem
import eu.junak.ttrpg.companion.core.model.character.QuickUseItem
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GearPresentationTest {

    @Test
    fun onlyCarriedItemsAreListed() {
        val items = listOf(
            GearItem("rations", "Rations", 6, "carried", "Backpack"),
            GearItem("mail", "Chain Mail", 1, "equipped", null),
            GearItem("tent", "Tent", 1, "stored", null),
        )
        assertEquals(listOf("rations"), carriedItems(items).map { it.id })
    }

    @Test
    fun weightsHaveOneDecimalInTheReadersFormat() {
        assertEquals("97.6", formatPounds(97.62, Locale.ENGLISH))
        assertEquals("270", formatPounds(270.0, Locale.ENGLISH))
        assertEquals("97,6", formatPounds(97.62, Locale.forLanguageTag("cs")))
    }

    @Test
    fun anItemIsUsedWhileItIsAvailableAndLeft() {
        val potion = QuickUseItem("potions", "Potion of Healing", 2, true, "carried", "Belt pouch")
        assertTrue(canUse(potion))
        assertFalse(canUse(potion.copy(available = false)))
        assertFalse(canUse(potion.copy(quantity = 0)))
    }

    @Test
    fun featuresNameTheirSourceAndLevel() {
        assertEquals("Paladin 1", featureSourceLabel(FeatureSource("class", "paladin", "Paladin", 1)))
        assertEquals("Oath of Devotion 3", featureSourceLabel(FeatureSource("subclass", "oath-of-devotion", "Oath of Devotion", 3)))
        assertEquals("a source without a name", "elf", featureSourceLabel(FeatureSource("species", "elf", null, null)))
        assertEquals("", featureSourceLabel(FeatureSource("", null, null, null)))
    }

    @Test
    fun myCharacterIsOnePerSite() {
        assertEquals("bren", myCharacterAfter(null, "bren", on = true))
        assertEquals("switching another on replaces the first", "mira", myCharacterAfter("bren", "mira", on = true))
        assertNull(myCharacterAfter("bren", "bren", on = false))
        assertEquals("switching off on another character leaves it", "bren", myCharacterAfter("bren", "mira", on = false))
    }
}
