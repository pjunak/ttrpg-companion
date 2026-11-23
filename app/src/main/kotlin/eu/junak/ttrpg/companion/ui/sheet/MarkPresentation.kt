package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.CauseKind
import eu.junak.ttrpg.companion.core.model.character.ChangeKind
import eu.junak.ttrpg.companion.core.model.character.EffectCause
import eu.junak.ttrpg.companion.core.model.character.EffectChange
import eu.junak.ttrpg.companion.core.model.character.EffectTone
import eu.junak.ttrpg.companion.core.model.character.ValueMark
import eu.junak.ttrpg.companion.ui.components.MarkedValue

/** A value a sheet screen shows that temporary effects can change. */
sealed interface Shown {
    data object Armor : Shown

    data object Speed : Shown

    data object Initiative : Shown

    data object MaxHitPoints : Shown

    data object PassivePerception : Shown

    /** An ability's check, shown as its modifier; [ability] is the sheet's ID ("STR"). */
    data class Check(val ability: String) : Shown

    data class Save(val ability: String) : Shown

    data class Skill(val skill: String) : Shown

    data class Sense(val sense: String) : Shown

    /** An attack row by its stable key ([eu.junak.ttrpg.companion.core.model.character.Attack.key]). */
    data class AttackBonus(val attack: String) : Shown

    data class Damage(val attack: String) : Shown

    data class SpellAttack(val classId: String) : Shown

    data class SaveDc(val classId: String) : Shown
}

/**
 * The Engine's path and row of a shown value (`contract/README.md#temporary-effects`):
 * the one place a screen's value meets a mark. List rows go by key, never by position.
 */
fun markTarget(shown: Shown): Pair<String, String?> = when (shown) {
    Shown.Armor -> "derived.armorClass" to null
    Shown.Speed -> "derived.speed" to null
    Shown.Initiative -> "derived.initiative" to null
    Shown.MaxHitPoints -> "derived.maxHp" to null
    Shown.PassivePerception -> "derived.passivePerception" to null
    is Shown.Check -> "abilities.${shown.ability}.mod" to null
    is Shown.Save -> "saves.${shown.ability}.total" to null
    is Shown.Skill -> "skills.${shown.skill}.total" to null
    is Shown.Sense -> "senses.${shown.sense}" to null
    is Shown.AttackBonus -> "weapons.attackBonus" to shown.attack
    is Shown.Damage -> "weapons.damage" to shown.attack
    is Shown.SpellAttack -> "spellcasting.perClass.spellAttack" to shown.classId
    is Shown.SaveDc -> "spellcasting.perClass.saveDC" to shown.classId
}

/** The mark of a shown value; null when nothing temporary changes it. */
fun markOf(marks: List<ValueMark>, shown: Shown): ValueMark? {
    val (path, row) = markTarget(shown)
    return marks.firstOrNull { it.path == path && it.row == row }
}

/**
 * The tone of a roll tag (Adv or Dis): that of the temporary causes that give
 * or cancel into this roll mark, so it matches them; null, a plain tag, when
 * no temporary cause does (armor's Stealth Disadvantage).
 */
fun tagTone(mark: ValueMark?, disadvantage: Boolean): EffectTone? {
    val wanted = if (disadvantage) ChangeKind.DISADVANTAGE else ChangeKind.ADVANTAGE
    val tones = mark?.causes.orEmpty().filter { cause -> cause.changes.any { it.kind == wanted } }.map { it.tone }.distinct()
    return when {
        tones.isEmpty() -> null
        tones.size == 1 -> tones.single()
        else -> EffectTone.MIXED
    }
}

/** The texts a cause is worded with, in the reader's language. */
data class MarkWords(
    val onTheRoll: (String) -> String,
    val setTo: (String) -> String,
    val advantage: String,
    val disadvantage: String,
    val changed: String,
    val level: (String, Int) -> String,
    val sentence: (name: String, shown: String, causes: String) -> String,
)

/** What one change does: "+3", "−4 on the roll, not in the number", "Disadvantage", "Set to 0". */
fun changeText(change: EffectChange, words: MarkWords): String = when (change.kind) {
    ChangeKind.ADD -> signed(change.value ?: 0).let { if (change.included == false) words.onTheRoll(it) else it }
    ChangeKind.SET -> words.setTo((change.value ?: 0).toString())
    ChangeKind.ADVANTAGE -> words.advantage
    ChangeKind.DISADVANTAGE -> words.disadvantage
    ChangeKind.OTHER -> words.changed
}

/** A cause's name as the condition chips show it: with its level when it has more than one. */
fun causeName(cause: EffectCause, words: MarkWords): String =
    cause.level?.takeIf { it > 1 }?.let { words.level(cause.name, it) } ?: cause.name

/** What a cause does: "Poisoned: Disadvantage". */
fun causeText(cause: EffectCause, words: MarkWords): String =
    causeName(cause, words) + ": " + cause.changes.joinToString(", ") { changeText(it, words) }

/** The one sentence a screen reader hears: "Armor 20, changed for now by Shrine blessing: +1". */
fun markSentence(name: String, shown: String, mark: ValueMark, words: MarkWords): String =
    words.sentence(name, shown, mark.causes.joinToString("; ") { causeText(it, words) })

@Composable
fun markWords(): MarkWords {
    val resources = LocalResources.current
    return MarkWords(
        onTheRoll = { resources.getString(R.string.mark_on_the_roll, it) },
        setTo = { resources.getString(R.string.mark_set_to, it) },
        advantage = resources.getString(R.string.mark_advantage),
        disadvantage = resources.getString(R.string.mark_disadvantage),
        changed = resources.getString(R.string.mark_changed),
        level = { name, value -> resources.getString(R.string.sheet_condition_level, name, value) },
        sentence = { name, shown, causes -> resources.getString(R.string.mark_sentence, name, shown, causes) },
    )
}

/** The cause's kind in the causes sheet: condition, active feature, timed boon. */
@Composable
fun causeKindName(kind: CauseKind): String = stringResource(
    when (kind) {
        CauseKind.CONDITION -> R.string.mark_kind_condition
        CauseKind.ACTIVE_FEATURE -> R.string.mark_kind_active_feature
        CauseKind.TIMED_GRANT -> R.string.mark_kind_timed_grant
    },
)

/** A shown value with its mark, ready for [eu.junak.ttrpg.companion.ui.components.MarkedText]; null when unmarked. */
@Composable
fun marked(marks: List<ValueMark>, shown: Shown, name: String, text: String): MarkedValue? {
    val mark = markOf(marks, shown) ?: return null
    return MarkedValue(mark, name, text, markSentence(name, text, mark, markWords()))
}
