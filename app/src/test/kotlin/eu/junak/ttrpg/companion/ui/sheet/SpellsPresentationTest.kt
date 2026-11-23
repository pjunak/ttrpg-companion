package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.CastableSpell
import eu.junak.ttrpg.companion.core.model.character.ClassCasting
import eu.junak.ttrpg.companion.core.model.character.Concentration
import eu.junak.ttrpg.companion.core.model.character.GrantedCasting
import eu.junak.ttrpg.companion.core.model.character.LimitedUse
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.ResourceKind
import eu.junak.ttrpg.companion.core.model.character.SheetSpells
import eu.junak.ttrpg.companion.core.model.character.SpellCaster
import eu.junak.ttrpg.companion.core.model.character.SpellSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpellsPresentationTest {

    private val paladin = SpellCaster("paladin", "Paladin", 5, 13, 5)
    private val slots = listOf(SpellSlot("slot-1", 1, 4, 3, false), SpellSlot("slot-2", 2, 2, 0, false))

    private fun spell(
        ref: String,
        level: Int = 1,
        concentration: Boolean = false,
        classes: List<ClassCasting> = emptyList(),
        rituals: List<String> = emptyList(),
        grants: List<GrantedCasting> = emptyList(),
    ) = CastableSpell(ref, ref, level, "Action", "30 feet", null, null, null, concentration, rituals.isNotEmpty(), classes, rituals, grants)

    private fun charge(key: String, left: Int) = LimitedUse(key, "free cast", "charge", ResourceKind.CHARGE, 1, left, 1 - left)

    @Test
    fun levelsReadAsRomanNumeralsAndTriggersAreShortened() {
        assertEquals("I", romanLevel(1))
        assertEquals("IX", romanLevel(9))
        assertEquals("10", romanLevel(10))
        assertEquals("Reaction", shortCastingTime("Reaction, which you take when you are hit by an attack roll"))
        assertEquals("Bonus Action", shortCastingTime("Bonus Action"))
    }

    @Test
    fun aClassSpellOffersEachSlotWithWhatIsLeft() {
        val cure = spell("cure-wounds", classes = listOf(ClassCasting("paladin", listOf("slot-1", "slot-2"))))
        val options = castOptions(cure, slots, listOf(paladin))
        assertEquals(
            listOf(
                CastOption(CastKind.SLOT, PlayChange.CastSpell("paladin", "cure-wounds", "slot-1"), "slot-1", 1, 3),
                CastOption(CastKind.SLOT, PlayChange.CastSpell("paladin", "cure-wounds", "slot-2"), "slot-2", 2, 0),
            ),
            options,
        )
        assertFalse("no level II slot is left", options[1].available)
        assertEquals("the only usable one starts chosen", options[0], preselectedOption(options))
    }

    @Test
    fun aGrantedFreeCastComesFirstAndAClassSlotIsOfferedOnce() {
        val charge = charge("charge:find-steed", 1)
        val steed = spell(
            "find-steed",
            level = 2,
            classes = listOf(ClassCasting("paladin", listOf("slot-2"))),
            grants = listOf(GrantedCasting("feature:find-steed", listOf(charge.key, "slot-2"), "1/long", charge, atWill = false)),
        )
        val options = castOptions(steed, listOf(SpellSlot("slot-2", 2, 2, 2, false)), listOf(paladin))
        assertEquals(
            listOf(
                CastOption(CastKind.FREE, PlayChange.CastGrantedSpell("feature:find-steed", charge.key), left = 1),
                CastOption(CastKind.SLOT, PlayChange.CastSpell("paladin", "find-steed", "slot-2"), "slot-2", 2, 2),
            ),
            options,
        )
        assertNull("two can be used: the player chooses", preselectedOption(options))
        assertEquals(1, freeCastsLeft(steed))
        assertNull("the free cast is spent", freeCastsLeft(steed.copy(grants = listOf(steed.grants[0].copy(freeUses = charge(charge.key, 0))))))
    }

    @Test
    fun cantripsAtWillPactSlotsAndRituals() {
        val pact = SpellSlot("pact-slot", 1, 2, 2, true)
        val warlock = SpellCaster("warlock", "Warlock", 2, 12, 4)
        val blast = castOptions(spell("eldritch-blast", level = 0, classes = listOf(ClassCasting("warlock", listOf("")))), listOf(pact), listOf(warlock))
        assertEquals(listOf(CastOption(CastKind.NO_SLOT, PlayChange.CastSpell("warlock", "eldritch-blast", ""))), blast)

        val armor = spell("mage-armor", grants = listOf(GrantedCasting("invocation:armor", listOf("", "pact-slot"), null, null, atWill = true)))
        assertEquals(
            listOf(
                CastOption(CastKind.AT_WILL, PlayChange.CastGrantedSpell("invocation:armor", "")),
                CastOption(CastKind.PACT_SLOT, PlayChange.CastGrantedSpell("invocation:armor", "pact-slot"), "pact-slot", 1, 2),
            ),
            castOptions(armor, listOf(pact), listOf(warlock)),
        )

        val wizard = SpellCaster("wizard", "Wizard", 5, 15, 7)
        val familiar = spell("find-familiar", rituals = listOf("wizard"))
        assertEquals(listOf(CastOption(CastKind.RITUAL, PlayChange.CastRitual("wizard", "find-familiar"))), castOptions(familiar, emptyList(), listOf(wizard)))
    }

    @Test
    fun severalClassesNameTheirOptions() {
        val bless = spell("bless", classes = listOf(ClassCasting("paladin", listOf("slot-1")), ClassCasting("cleric", listOf("slot-1"))))
        val cleric = SpellCaster("cleric", "Cleric", 1, 12, 4)
        assertEquals(listOf("Paladin", "Cleric"), castOptions(bless, slots, listOf(paladin, cleric)).map { it.source })
    }

    @Test
    fun sectionsGoByLevelWithTheirSlotsAndPactSlotsApart() {
        val spells = SheetSpells(
            casters = listOf(paladin),
            slots = slots + SpellSlot("slot-3", 3, 1, 1, false) + SpellSlot("pact-slot", 2, 2, 2, true),
            spells = listOf(spell("light", level = 0), spell("bless"), spell("aid", level = 2)),
        )
        val sections = spellSections(spells)
        assertEquals(listOf(0, 1, 2, 3), sections.map { it.level })
        assertEquals(listOf("slot-1"), sections[1].slots.map { it.key })
        assertEquals("a level with slots and no spells still shows its slots", emptyList<CastableSpell>(), sections[3].spells)
        assertTrue(sections.none { section -> section.slots.any { it.pact } })
        assertTrue(hasSpells(spells))
        assertFalse(hasSpells(SheetSpells(emptyList(), emptyList(), emptyList())))
    }

    @Test
    fun aSummaryDropsAHeadingThatRepeatsTheName() {
        assertEquals("*Level 1*\n\nHeals.", spellSummary("# Cure Wounds\n*Level 1*\n\nHeals.", "Cure Wounds"))
        assertEquals("Heals.", spellSummary("Heals.", "Cure Wounds"))
        assertEquals("another heading stays", "## Notes\nHeals.", spellSummary("## Notes\nHeals.", "Cure Wounds"))
    }

    @Test
    fun aConcentrationSpellEndsTheOneConcentratedOn() {
        val shield = spell("shield-of-faith", concentration = true)
        assertEquals("Bless", concentrationEnded(shield, Concentration("bless", "Bless")))
        assertNull(concentrationEnded(shield, null))
        assertNull(concentrationEnded(spell("command"), Concentration("bless", "Bless")))
    }
}
