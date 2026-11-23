package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.Ability
import eu.junak.ttrpg.companion.core.model.character.Proficiency
import eu.junak.ttrpg.companion.core.model.character.Skill
import eu.junak.ttrpg.companion.core.model.character.SkillRow
import org.junit.Assert.assertEquals
import org.junit.Test

class AbilitiesPresentationTest {

    @Test
    fun everyAbilitySkillAndTrainingHasItsOwnName() {
        assertEquals(Ability.entries.size, Ability.entries.map(::abilityName).toSet().size)
        assertEquals(Skill.entries.size, Skill.entries.map(::skillName).toSet().size)
        assertEquals(Proficiency.entries.size, Proficiency.entries.map(::proficiencyName).toSet().size)
    }

    @Test
    fun skillsFillTwoColumnsTopToBottomInTheSheetsOrder() {
        val skills = Skill.entries.map { SkillRow(it.id, it, "STR", 0, Proficiency.NONE, false) }
        val (left, right) = skillColumns(skills)
        assertEquals(skills.take(9), left)
        assertEquals(skills.drop(9), right)
        val odd = skills.take(5)
        assertEquals("the first column holds the larger half", odd.take(3) to odd.drop(3), skillColumns(odd))
        assertEquals(emptyList<SkillRow>() to emptyList<SkillRow>(), skillColumns(emptyList()))
    }
}
