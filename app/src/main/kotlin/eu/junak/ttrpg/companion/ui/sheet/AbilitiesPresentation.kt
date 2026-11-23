package eu.junak.ttrpg.companion.ui.sheet

import androidx.annotation.StringRes
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.Ability
import eu.junak.ttrpg.companion.core.model.character.Proficiency
import eu.junak.ttrpg.companion.core.model.character.Skill
import eu.junak.ttrpg.companion.core.model.character.SkillRow

/** An ability's full name; the Czech names are the Character Sheets add-on's, so the phone and the website agree. */
@StringRes
fun abilityName(ability: Ability): Int = when (ability) {
    Ability.STR -> R.string.ability_strength
    Ability.DEX -> R.string.ability_dexterity
    Ability.CON -> R.string.ability_constitution
    Ability.INT -> R.string.ability_intelligence
    Ability.WIS -> R.string.ability_wisdom
    Ability.CHA -> R.string.ability_charisma
}

/** A skill's name, as the Character Sheets add-on words it. */
@StringRes
fun skillName(skill: Skill): Int = when (skill) {
    Skill.ACROBATICS -> R.string.skill_acrobatics
    Skill.ANIMAL_HANDLING -> R.string.skill_animal_handling
    Skill.ARCANA -> R.string.skill_arcana
    Skill.ATHLETICS -> R.string.skill_athletics
    Skill.DECEPTION -> R.string.skill_deception
    Skill.HISTORY -> R.string.skill_history
    Skill.INSIGHT -> R.string.skill_insight
    Skill.INTIMIDATION -> R.string.skill_intimidation
    Skill.INVESTIGATION -> R.string.skill_investigation
    Skill.MEDICINE -> R.string.skill_medicine
    Skill.NATURE -> R.string.skill_nature
    Skill.PERCEPTION -> R.string.skill_perception
    Skill.PERFORMANCE -> R.string.skill_performance
    Skill.PERSUASION -> R.string.skill_persuasion
    Skill.RELIGION -> R.string.skill_religion
    Skill.SLEIGHT_OF_HAND -> R.string.skill_sleight_of_hand
    Skill.STEALTH -> R.string.skill_stealth
    Skill.SURVIVAL -> R.string.skill_survival
}

/** How a skill's training is read out. */
@StringRes
fun proficiencyName(proficiency: Proficiency): Int = when (proficiency) {
    Proficiency.NONE -> R.string.sheet_untrained
    Proficiency.PROFICIENT -> R.string.sheet_proficient
    Proficiency.EXPERTISE -> R.string.sheet_expertise
}

/**
 * The skills in two columns filled top to bottom, in the sheet's own order
 * (the website's), whatever the language: the first column holds the larger half.
 */
fun skillColumns(skills: List<SkillRow>): Pair<List<SkillRow>, List<SkillRow>> {
    val half = (skills.size + 1) / 2
    return skills.take(half) to skills.drop(half)
}
