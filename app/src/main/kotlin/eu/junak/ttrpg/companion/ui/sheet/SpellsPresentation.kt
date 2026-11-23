package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.CastableSpell
import eu.junak.ttrpg.companion.core.model.character.Concentration
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.SheetSpells
import eu.junak.ttrpg.companion.core.model.character.SpellCaster
import eu.junak.ttrpg.companion.core.model.character.SpellSlot

private val romanNumerals = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX")

/** A spell level as the sheet heads it, "I" to "IX"; any other level as a number. */
fun romanLevel(level: Int): String = romanNumerals.getOrNull(level - 1) ?: level.toString()

/** The casting time the list shows: a trigger after a comma ("Reaction, which you take …") is left for the cast sheet. */
fun shortCastingTime(time: String): String = time.substringBefore(",").trim()

/** One level's part of the Spells tab: its spells and its (not pact) slots. Level 0 holds the cantrips. */
data class SpellSection(val level: Int, val slots: List<SpellSlot>, val spells: List<CastableSpell>)

/**
 * The Spells tab's sections, cantrips first, then each level that has spells
 * or slots, so slots for casting higher stay in view. Pact slots are shown
 * apart, with their own level.
 */
fun spellSections(spells: SheetSpells): List<SpellSection> {
    val slots = spells.slots.filterNot { it.pact }
    return (spells.spells.map { it.level } + slots.map { it.level }).distinct().sorted().map { level ->
        SpellSection(level, slots.filter { it.level == level }, spells.spells.filter { it.level == level })
    }
}

/** Whether the character has anything for the Spells tab to show. */
fun hasSpells(spells: SheetSpells): Boolean = spells.casters.isNotEmpty() || spells.spells.isNotEmpty() || spells.slots.isNotEmpty()

/** Free casts left from the spell's grants, for the list's "free" tag; null when there are none. */
fun freeCastsLeft(spell: CastableSpell): Int? = spell.grants.mapNotNull { it.freeUses?.left }.sum().takeIf { it > 0 }

/** How a cast option spends: in the order the cast sheet lists them. */
enum class CastKind { FREE, AT_WILL, NO_SLOT, SLOT, PACT_SLOT, RITUAL }

/**
 * One way to cast a spell now. [left] is what remains of what it spends
 * (null when it spends nothing); [level] and [slotKey] name a slot;
 * [source] names the class when several classes can cast the spell.
 */
data class CastOption(
    val kind: CastKind,
    val change: PlayChange,
    val slotKey: String? = null,
    val level: Int? = null,
    val left: Int? = null,
    val source: String? = null,
) {
    val available: Boolean get() = left == null || left > 0
}

/**
 * Every way [spell] can be cast now: each class's slots (higher ones too) or
 * no slot for a cantrip, each ritual casting, and each grant's free use, at
 * will casting or slots. A grant's slot that a class already offers is left
 * out, so a slot appears once.
 */
fun castOptions(spell: CastableSpell, slots: List<SpellSlot>, casters: List<SpellCaster>): List<CastOption> {
    val bySlot = slots.associateBy { it.key }
    val manyClasses = (spell.classes.map { it.classId } + spell.ritualClasses).distinct().size > 1
    fun source(classId: String) = if (manyClasses) casters.firstOrNull { it.classId == classId }?.name ?: classId else null
    fun slot(key: String, change: PlayChange, source: String?): CastOption {
        val slot = bySlot[key]
        return CastOption(if (slot?.pact == true) CastKind.PACT_SLOT else CastKind.SLOT, change, key, slot?.level, slot?.left, source)
    }
    val options = mutableListOf<CastOption>()
    val classSlots = mutableSetOf<String>()
    for (casting in spell.classes) {
        for (key in casting.slots) {
            val change = PlayChange.CastSpell(casting.classId, spell.ref, key)
            options += if (key.isEmpty()) {
                CastOption(CastKind.NO_SLOT, change, source = source(casting.classId))
            } else {
                classSlots += key
                slot(key, change, source(casting.classId))
            }
        }
    }
    for (classId in spell.ritualClasses) options += CastOption(CastKind.RITUAL, PlayChange.CastRitual(classId, spell.ref), source = source(classId))
    for (grant in spell.grants) {
        for (key in grant.slots) {
            val change = PlayChange.CastGrantedSpell(grant.key, key)
            val free = grant.freeUses
            when {
                key.isEmpty() -> options += CastOption(CastKind.AT_WILL, change)
                free != null && key == free.key -> options += CastOption(CastKind.FREE, change, left = free.left)
                key !in classSlots -> options += slot(key, change, null)
            }
        }
    }
    // Slots of any kind by level; the rest in the order of CastKind.
    return options.sortedWith(compareBy({ if (it.kind == CastKind.PACT_SLOT) CastKind.SLOT.ordinal else it.kind.ordinal }, { it.level ?: 0 }))
}

/** The option the cast sheet starts with: the only one that can be used, if there is just one. */
fun preselectedOption(options: List<CastOption>): CastOption? = options.filter { it.available }.singleOrNull()

/**
 * A spell's summary as the cast sheet shows it: the rules data's Markdown
 * without a leading heading that only repeats the spell's [name].
 */
fun spellSummary(summary: String, name: String): String {
    val lines = summary.trim().lines()
    val heading = lines.firstOrNull()?.trim()?.trimStart('#')?.trim()
    val text = if (lines.firstOrNull()?.trimStart()?.startsWith("#") == true && heading.equals(name, ignoreCase = true)) lines.drop(1) else lines
    return text.joinToString("\n").trim()
}

/** The spell whose concentration casting [spell] ends, by name; null when nothing ends. */
fun concentrationEnded(spell: CastableSpell, current: Concentration?): String? = current?.name?.takeIf { spell.concentration }
