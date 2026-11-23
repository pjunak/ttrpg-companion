package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.Attack
import eu.junak.ttrpg.companion.core.model.character.FeatureToggle
import eu.junak.ttrpg.companion.core.model.character.HitDice
import eu.junak.ttrpg.companion.core.model.character.LimitedUse
import kotlin.random.Random

/** A limited use with at most this many uses shows pips; a larger pool shows a count. */
const val PIP_LIMIT = 5

/** How a limited use is spent: one use at a time beside its pips, or an amount from a large pool such as Lay on Hands. */
enum class UseShape { PIPS, AMOUNT }

fun useShape(use: LimitedUse): UseShape = if (use.maximum in 1..PIP_LIMIT) UseShape.PIPS else UseShape.AMOUNT

/** Whether [count] uses of [use] can be spent, or, for a negative count, given back. */
fun canSpend(use: LimitedUse, count: Int): Boolean = when {
    count > 0 -> count <= use.left
    count < 0 -> -count <= use.spent
    else -> false
}

/** A feature switch can be used while the feature is available, or to switch it off while it is on. */
fun toggleUsable(toggle: FeatureToggle): Boolean = toggle.available || toggle.active

/** The use that switching [toggle] on spends, by name (Channel Divinity for Sacred Weapon); null when it spends none. */
fun toggleSpends(toggle: FeatureToggle, uses: List<LimitedUse>): String? =
    toggle.resourceKey?.let { key -> uses.firstOrNull { it.key == key }?.name ?: key }

/** The size of a hit die such as "d10"; null for a die the app cannot read. */
fun dieSize(die: String): Int? = die.removePrefix("d").toIntOrNull()?.takeIf { it > 0 }

fun canSpendHitDie(dice: HitDice): Boolean = dice.left > 0 && dieSize(dice.die) != null

/** A roll of a die with [size] faces, made on the phone. */
fun rollDie(size: Int, random: Random = Random.Default): Int = random.nextInt(1, size + 1)

/**
 * An attack's line under its name: damage and type, the weapon mastery the
 * character can use, then its range ("30/120") or, without one, its reach,
 * worded by [range] and [reach].
 */
fun attackSummary(attack: Attack, range: (String) -> String, reach: (Int) -> String): String {
    val distance = attack.range?.let { r -> range(if (r.long > r.normal) "${r.normal}/${r.long}" else "${r.normal}") }
        ?: attack.reach?.let(reach)
    return listOfNotNull(
        listOf(attack.damage, attack.damageType).filter(String::isNotEmpty).joinToString(" ").ifEmpty { null },
        attack.mastery?.takeIf { attack.masteryActive },
        distance,
    ).joinToString(" · ")
}
