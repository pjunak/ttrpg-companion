package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.Attack
import eu.junak.ttrpg.companion.core.model.character.AttackRange
import eu.junak.ttrpg.companion.core.model.character.FeatureToggle
import eu.junak.ttrpg.companion.core.model.character.HitDice
import eu.junak.ttrpg.companion.core.model.character.LimitedUse
import eu.junak.ttrpg.companion.core.model.character.ResourceKind
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CombatPresentationTest {

    private val channel = LimitedUse("channel-divinity", "Channel Divinity", "pool", ResourceKind.POOL, 2, 1, 1)
    private val layOnHands = LimitedUse("lay-on-hands", "Lay on Hands", "pool", ResourceKind.POOL, 25, 20, 5)

    @Test
    fun smallPoolsShowPipsAndLargeOnesAnAmount() {
        assertEquals(UseShape.PIPS, useShape(channel))
        assertEquals(UseShape.PIPS, useShape(channel.copy(maximum = PIP_LIMIT)))
        assertEquals(UseShape.AMOUNT, useShape(layOnHands))
        assertEquals("nothing to pick from", UseShape.AMOUNT, useShape(channel.copy(maximum = 0, left = 0, spent = 0)))
    }

    @Test
    fun usesAreSpentFromWhatIsLeftAndGivenBackFromWhatIsSpent() {
        assertTrue(canSpend(layOnHands, 20))
        assertFalse(canSpend(layOnHands, 21))
        assertTrue("give back", canSpend(layOnHands, -5))
        assertFalse(canSpend(layOnHands, -6))
        assertFalse(canSpend(layOnHands, 0))
        assertFalse("none left", canSpend(channel.copy(left = 0, spent = 2), 1))
    }

    @Test
    fun aSwitchSaysWhichUseItSpends() {
        val sacred = FeatureToggle("feature:sacred-weapon", "Sacred Weapon", active = false, available = true, resourceKey = "channel-divinity")
        assertEquals("Channel Divinity", toggleSpends(sacred, listOf(layOnHands, channel)))
        assertEquals("an unknown resource by its key", "pact-slot", toggleSpends(sacred.copy(resourceKey = "pact-slot"), listOf(channel)))
        assertNull(toggleSpends(sacred.copy(resourceKey = null), listOf(channel)))

        assertTrue(toggleUsable(sacred))
        assertFalse("no use left to switch it on", toggleUsable(sacred.copy(available = false)))
        assertTrue("it can always be switched off", toggleUsable(sacred.copy(available = false, active = true)))
    }

    @Test
    fun hitDiceRollWithinTheirSize() {
        assertEquals(10, dieSize("d10"))
        assertNull(dieSize("10d"))
        assertNull(dieSize("d0"))
        val dice = HitDice("hit-dice-d10", "d10", 5, 4, 1)
        assertTrue(canSpendHitDie(dice))
        assertFalse(canSpendHitDie(dice.copy(left = 0, spent = 5)))
        assertFalse(canSpendHitDie(dice.copy(die = "dX")))
        val random = Random(7)
        val rolls = List(500) { rollDie(10, random) }
        assertEquals((1..10).toSet(), rolls.toSet())
    }

    @Test
    fun anAttackLineNamesDamageMasteryAndDistance() {
        val range = { span: String -> "$span ft" }
        val reach = { feet: Int -> "reach $feet ft" }
        val longsword = Attack("Longsword", 7, "1d8 +4", "slashing", "1d10 +4", listOf("versatile"), "Sap", true, null, 5, "sword")
        assertEquals("1d8 +4 slashing · Sap · reach 5 ft", attackSummary(longsword, range, reach))
        assertEquals("a mastery the character lacks is left out", "1d8 +4 slashing · reach 5 ft", attackSummary(longsword.copy(masteryActive = false), range, reach))
        val javelin = Attack("Javelin", 7, "1d6 +4", "piercing", null, listOf("thrown"), "Slow", true, AttackRange(30, 120, true), 5, "javelin")
        assertEquals("a thrown weapon shows its range", "1d6 +4 piercing · Slow · 30/120 ft", attackSummary(javelin, range, reach))
        assertEquals("5 bludgeoning", attackSummary(Attack("Unarmed Strike", 7, "5", "bludgeoning", null, emptyList(), null, false, null, null, null), range, reach))
        assertEquals("one distance", "1d4 · 60 ft", attackSummary(javelin.copy(damage = "1d4", damageType = "", mastery = null, range = AttackRange(60, 0, false)), range, reach))
    }
}
