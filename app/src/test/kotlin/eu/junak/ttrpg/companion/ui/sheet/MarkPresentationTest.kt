package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.CauseKind
import eu.junak.ttrpg.companion.core.model.character.ChangeKind
import eu.junak.ttrpg.companion.core.model.character.EffectCause
import eu.junak.ttrpg.companion.core.model.character.EffectChange
import eu.junak.ttrpg.companion.core.model.character.EffectTone
import eu.junak.ttrpg.companion.core.model.character.ValueMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkPresentationTest {

    private val words = MarkWords(
        onTheRoll = { "$it on the roll, not in the number" },
        setTo = { "Set to $it" },
        advantage = "Advantage",
        disadvantage = "Disadvantage",
        changed = "Changed",
        level = { name, level -> "$name $level" },
        sentence = { name, shown, causes -> "$name $shown, changed for now by $causes" },
    )

    private val poisoned = EffectCause(CauseKind.CONDITION, "poisoned", "Poisoned", "Poisoned", null, EffectTone.PENALTY, listOf(EffectChange(ChangeKind.DISADVANTAGE, null, null)))
    private val exhaustion = EffectCause(CauseKind.CONDITION, "exhaustion", "Exhaustion", "Exhaustion", 2, EffectTone.PENALTY, listOf(EffectChange(ChangeKind.ADD, -4, false)))
    private val sacred = EffectCause(CauseKind.ACTIVE_FEATURE, "paladin:sacred-weapon", "Sacred Weapon", null, null, EffectTone.BONUS, listOf(EffectChange(ChangeKind.ADD, 3, true)))

    @Test
    fun everyShownValueHasItsEnginePath() {
        assertEquals("derived.armorClass" to null, markTarget(Shown.Armor))
        assertEquals("derived.speed" to null, markTarget(Shown.Speed))
        assertEquals("derived.initiative" to null, markTarget(Shown.Initiative))
        assertEquals("derived.maxHp" to null, markTarget(Shown.MaxHitPoints))
        assertEquals("derived.passivePerception" to null, markTarget(Shown.PassivePerception))
        assertEquals("abilities.STR.mod" to null, markTarget(Shown.Check("STR")))
        assertEquals("saves.CON.total" to null, markTarget(Shown.Save("CON")))
        assertEquals("skills.stealth.total" to null, markTarget(Shown.Skill("stealth")))
        assertEquals("senses.darkvision" to null, markTarget(Shown.Sense("darkvision")))
        assertEquals("weapons.attackBonus" to "item:sword", markTarget(Shown.AttackBonus("item:sword")))
        assertEquals("weapons.damage" to "unarmed", markTarget(Shown.Damage("unarmed")))
        assertEquals("spellcasting.perClass.spellAttack" to "paladin", markTarget(Shown.SpellAttack("paladin")))
        assertEquals("spellcasting.perClass.saveDC" to "wizard", markTarget(Shown.SaveDc("wizard")))
    }

    @Test
    fun aMarkIsFoundByPathAndRow() {
        val sword = ValueMark("weapons.attackBonus", "item:sword", EffectTone.MIXED, listOf(poisoned, sacred))
        val marks = listOf(sword, ValueMark("derived.speed", null, EffectTone.PENALTY, listOf(exhaustion)))
        assertEquals(sword, markOf(marks, Shown.AttackBonus("item:sword")))
        assertNull("another row is not this one", markOf(marks, Shown.AttackBonus("unarmed")))
        assertEquals(EffectTone.PENALTY, markOf(marks, Shown.Speed)?.tone)
        assertNull(markOf(marks, Shown.Armor))
        assertNull(markOf(emptyList(), Shown.Speed))
    }

    @Test
    fun aRollTagTakesTheToneOfTheTemporaryCauseOnly() {
        assertEquals(EffectTone.PENALTY, tagTone(ValueMark("skills.athletics.total", null, EffectTone.PENALTY, listOf(poisoned, exhaustion)), disadvantage = true))
        // Exhaustion alone does not give the Disadvantage: armor's tag stays plain.
        assertNull(tagTone(ValueMark("skills.stealth.total", null, EffectTone.PENALTY, listOf(exhaustion)), disadvantage = true))
        assertNull("no mark, a plain tag", tagTone(null, disadvantage = true))
        assertNull(tagTone(ValueMark("skills.athletics.total", null, EffectTone.PENALTY, listOf(poisoned)), disadvantage = false))
    }

    @Test
    fun causesSayWhatTheyDo() {
        assertEquals("Poisoned: Disadvantage", causeText(poisoned, words))
        assertEquals("Exhaustion 2: −4 on the roll, not in the number", causeText(exhaustion, words))
        assertEquals("Sacred Weapon: +3", causeText(sacred, words))
        assertEquals("Set to 0", changeText(EffectChange(ChangeKind.SET, 0, true), words))
        assertEquals("Changed", changeText(EffectChange(ChangeKind.OTHER, null, null), words))
        assertEquals("Advantage", changeText(EffectChange(ChangeKind.ADVANTAGE, null, null), words))
        val levelOne = exhaustion.copy(level = 1)
        assertEquals("a level of one is not shown, as on the chips", "Exhaustion", causeName(levelOne, words))
    }

    @Test
    fun aScreenReaderHearsOneSentence() {
        val mark = ValueMark("weapons.attackBonus", "item:sword", EffectTone.MIXED, listOf(poisoned, exhaustion, sacred))
        assertEquals(
            "Longsword attack roll +10, changed for now by Poisoned: Disadvantage; Exhaustion 2: −4 on the roll, not in the number; Sacred Weapon: +3",
            markSentence("Longsword attack roll", "+10", mark, words),
        )
    }
}
