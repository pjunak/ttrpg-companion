package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.ClassLevel
import eu.junak.ttrpg.companion.core.model.character.Coins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GlancePresentationTest {

    @Test
    fun aCountNeedsBothNumbers() {
        assertEquals("6 / 6", countOf(6, 6))
        assertEquals("0 / 3", countOf(0, 3))
        assertNull(countOf(null, 3))
        assertNull(countOf(2, null))
    }

    @Test
    fun theWeightMeterFillsToTheCapacity() {
        assertEquals(0.5f, weightShare(135.0, 270.0)!!, 0.0001f)
        assertEquals("never past full", 1f, weightShare(300.0, 270.0)!!, 0.0001f)
        assertEquals("nothing carried", 0f, weightShare(0.0, 270.0)!!, 0.0001f)
        assertEquals("no capacity at all", 1f, weightShare(5.0, 0.0)!!, 0.0001f)
        assertNull("without a capacity there is no meter", weightShare(97.6, null))
        assertNull(weightShare(null, 270.0))
    }

    @Test
    fun theCoinStripShowsTheFiveCoinsInOrder() {
        val strip = coinStrip(listOf(Coins("gp", 87), Coins("cp", 12), Coins("pp", 2)))
        assertEquals(
            listOf(Coins("pp", 2), Coins("gp", 87), Coins("ep", 0), Coins("sp", 0), Coins("cp", 12)),
            strip,
        )
        assertEquals(
            "a coin the app does not know comes last",
            listOf("pp", "gp", "ep", "sp", "cp", "ap"),
            coinStrip(listOf(Coins("ap", 3), Coins("sp", 30))).map { it.coin },
        )
        assertEquals("no money is five zeros", List(5) { 0L }, coinStrip(emptyList()).map { it.amount })
        assertEquals(R.string.coin_electrum, coinName("ep"))
        assertNull(coinName("ap"))
    }

    @Test
    fun sensesAndSizesUseTheAddOnsNames() {
        assertEquals(R.string.sense_darkvision, senseName("darkvision"))
        assertNull(senseName("devils-sight"))
        assertEquals("Devils sight", readableId("devils-sight"))
        assertEquals(R.string.size_medium, sizeName("Medium"))
        assertEquals(R.string.size_gargantuan, sizeName("gargantuan"))
        assertNull(sizeName("Colossal"))
    }

    @Test
    fun theIdentityRowsSayOnlyWhatTheSheetHas() {
        val wizard = ClassLevel("wizard", "Wizard", 5, "evoker", "School of Evocation")
        assertEquals("Wizard 5 · School of Evocation", classFact(listOf(wizard)))
        assertEquals(
            "each class on its own line; a subclass without a name is read from its ID",
            "Paladin 4 · Oath of devotion\nWarlock 1",
            classFact(listOf(ClassLevel("paladin", "Paladin", 4, "oath-of-devotion", null), ClassLevel("warlock", "Warlock", 1, null, null))),
        )
        assertNull(classFact(emptyList()))
        assertEquals("Elf · Medium", speciesFact("Elf", "Medium"))
        assertEquals("Elf", speciesFact("Elf", null))
        assertEquals("Medium", speciesFact(null, "Medium"))
        assertNull(speciesFact(null, null))
    }
}
