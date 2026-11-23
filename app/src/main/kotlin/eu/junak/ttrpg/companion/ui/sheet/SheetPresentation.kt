package eu.junak.ttrpg.companion.ui.sheet

import eu.junak.ttrpg.companion.core.model.character.ActiveCondition
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus
import eu.junak.ttrpg.companion.core.model.character.ConditionOption
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.SheetHeader
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.SaveState
import eu.junak.ttrpg.companion.core.network.services.ServiceAvailability
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import kotlinx.serialization.json.JsonObject

/** The sheet's five tabs, in the bottom bar's order. */
enum class SheetTab { COMBAT, ABILITIES, SPELLS, GEAR, MORE }

/**
 * What a tab can do to the sheet. Changes are refused while [canWrite] is
 * false; made while one is being saved, they simply wait their turn.
 */
class SheetActions(
    val canWrite: Boolean,
    val play: (PlayChange) -> Unit,
    val edit: ((JsonObject) -> JsonObject) -> Unit,
    /** Opens the character's page on the website, where the sheet is built. */
    val openWebsite: () -> Unit,
)

/** The top bar's save badge. */
enum class SaveBadge { SAVED, SAVING, NOT_SAVED }

fun saveBadge(state: CharacterSessionState): SaveBadge = when {
    state.save is SaveState.NotSaved -> SaveBadge.NOT_SAVED
    state.save == SaveState.Saving || state.pending > 0 -> SaveBadge.SAVING
    else -> SaveBadge.SAVED
}

/** The one banner above the header, the most urgent first. */
sealed interface SheetBanner {
    /** A change's answer was lost; it waits with everything behind it. */
    data class NotSaved(val failure: ServiceFailure, val retrying: Boolean) : SheetBanner

    /** The sheet is the phone's copy, from [receivedAt]; [failure] once the site could not be reached. */
    data class DeviceCopy(val receivedAt: Long, val failure: ServiceFailure?) : SheetBanner

    data object RulesChanged : SheetBanner

    data object RulesUnavailable : SheetBanner
}

fun sheetBanner(state: CharacterSessionState): SheetBanner? {
    val save = state.save
    val copy = state.copy
    return when {
        save is SaveState.NotSaved -> SheetBanner.NotSaved(save.failure, save.retrying)
        copy != null -> SheetBanner.DeviceCopy(copy.receivedAt, copy.failure)
        state.answer?.rulesChanged == true -> SheetBanner.RulesChanged
        state.answer?.status == CharacterStatus.UNAVAILABLE -> SheetBanner.RulesUnavailable
        else -> null
    }
}

/** The header is dimmed while the rules changed or are unavailable: readable, not usable. */
fun headerDimmed(state: CharacterSessionState): Boolean =
    state.answer?.rulesChanged == true || state.answer?.status == CharacterStatus.UNAVAILABLE

/** "Human · Paladin 5", or with several classes "Human · Paladin 4 / Warlock 1". */
fun classLine(header: SheetHeader): String =
    listOfNotNull(header.species, header.classes.joinToString(" / ") { "${it.name} ${it.level}" }.ifEmpty { null }).joinToString(" · ")

/** A modifier with its sign: "+3", "−1" (a true minus sign), "+0". */
fun signed(value: Int): String = if (value >= 0) "+$value" else "−${-value}"

/** How much of the hit point bar is current hit points and how much temporary ones, each 0..1. */
fun hitPointShares(header: SheetHeader): Pair<Float, Float> {
    if (header.maxHitPoints <= 0) return 0f to 0f
    val current = (header.hitPoints.toFloat() / header.maxHitPoints).coerceIn(0f, 1f)
    val temporary = (header.temporaryHitPoints.toFloat() / header.maxHitPoints).coerceIn(0f, 1f - current)
    return current to temporary
}

/** A key of the hit point keypad. */
sealed interface KeypadKey {
    data class Digit(val digit: Int) : KeypadKey

    data object Clear : KeypadKey

    data object Delete : KeypadKey
}

/** The keypad holds up to this many digits. */
const val KEYPAD_DIGITS = 4

/** The keypad's number after [key]; a leading zero is replaced, and it never grows past [KEYPAD_DIGITS] digits. */
fun pressKey(number: String, key: KeypadKey): String = when (key) {
    KeypadKey.Clear -> ""
    KeypadKey.Delete -> number.dropLast(1)
    is KeypadKey.Digit -> when {
        number == "0" -> key.digit.toString()
        number.length >= KEYPAD_DIGITS -> number
        else -> number + key.digit
    }
}

/** The amount Damage, Heal and Temp HP send; null (they are off) while nothing is typed. */
fun keypadAmount(number: String): Int? = number.toIntOrNull()

/** One row of the condition picker; [level] is the level the character has it at now. */
data class ConditionChoice(val id: String, val name: String, val maximumLevel: Int, val enabled: Boolean, val level: Int?) {
    /** A condition with levels, such as exhaustion, asks for one. */
    val levelled: Boolean get() = maximumLevel > 1
}

/** The picker's rows, from the rules' condition options; one the character cannot get is off. */
fun conditionChoices(options: List<ConditionOption>, active: List<ActiveCondition>): List<ConditionChoice> =
    options.map { option ->
        ConditionChoice(option.id, option.name, option.maximumLevel.coerceAtLeast(1), option.canAdd, active.firstOrNull { it.id == option.id }?.level)
    }

/**
 * Whether a record page offers "Open sheet": on a signed-in site, when the host
 * says the sheet service is offered, or when it cannot be asked and the phone
 * holds a copy of this character's sheet. Before the host answers, it does not.
 */
fun offersSheet(signedIn: Boolean, availability: ServiceAvailability?, hasCopy: Boolean): Boolean = signedIn && when (availability) {
    is ServiceAvailability.Available -> true
    is ServiceAvailability.Unknown -> hasCopy
    ServiceAvailability.NotOffered, ServiceAvailability.HostTooOld, null -> false
}
