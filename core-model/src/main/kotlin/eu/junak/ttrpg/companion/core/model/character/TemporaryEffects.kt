package eu.junak.ttrpg.companion.core.model.character

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull

/** How a temporary change reads: a bonus, a penalty, or mixed (both, or neither). */
enum class EffectTone(val wire: String) { BONUS("bonus"), PENALTY("penalty"), MIXED("mixed") }

/** The play state a cause comes from. */
enum class CauseKind(val wire: String) { CONDITION("condition"), ACTIVE_FEATURE("active-feature"), TIMED_GRANT("timed-grant") }

/** What a cause does to a value. */
enum class ChangeKind(val wire: String) { ADD("add"), SET("set"), ADVANTAGE("advantage"), DISADVANTAGE("disadvantage"), OTHER("other") }

/**
 * One change: an [ADD][ChangeKind.ADD] or [SET][ChangeKind.SET] carries
 * [value] and [included], whether the shown value already holds it (a
 * condition's D20 adjustment does not: it applies to the roll).
 */
data class EffectChange(val kind: ChangeKind, val value: Int?, val included: Boolean?)

/**
 * A cause of a [ValueMark]: [id] is the condition's ID, the activation's key
 * or the grant's ID; [name] the record or grant name as it is; [labelKey] a
 * condition's rules-data label key and [level] its level when it has levels.
 */
data class EffectCause(
    val kind: CauseKind,
    val id: String,
    val name: String,
    val labelKey: String?,
    val level: Int?,
    val tone: EffectTone,
    val changes: List<EffectChange>,
)

/**
 * A value that temporary play state changes now, as the Engine measured it:
 * [path] as the explanations name it ("derived.armorClass", "saves.STR.total"),
 * [row] the stable key of a list row (an attack's [Attack.key], a caster's
 * class), its [tone] and [causes].
 */
data class ValueMark(val path: String, val row: String?, val tone: EffectTone, val causes: List<EffectCause>)

/**
 * Reads `evaluation.sheet.temporaryEffects` (Engine 6.6.0 through Character
 * Sheets 6.5.0). Absent means none: an older site, or an answer without an
 * evaluation. A mark with a tone, kind or shape this app does not know is
 * skipped, so a newer Engine cannot break the sheet.
 */
object TemporaryEffects {
    fun read(evaluationSheet: JsonObject?): List<ValueMark> =
        (evaluationSheet?.get("temporaryEffects") as? JsonArray).orEmpty().mapNotNull(::mark)

    private fun mark(element: JsonElement): ValueMark? {
        val value = element as? JsonObject ?: return null
        val causes = (value["causes"] as? JsonArray ?: return null).map { cause(it) ?: return null }
        if (causes.isEmpty()) return null
        return ValueMark(
            path = value.text("path") ?: return null,
            row = value.text("row")?.takeIf(String::isNotEmpty),
            tone = tone(value) ?: return null,
            causes = causes,
        )
    }

    private fun cause(element: JsonElement): EffectCause? {
        val value = element as? JsonObject ?: return null
        val kind = value.text("kind")?.let { wire -> CauseKind.entries.firstOrNull { it.wire == wire } } ?: return null
        return EffectCause(
            kind = kind,
            id = value.text("id") ?: return null,
            name = value.text("name") ?: return null,
            labelKey = value.text("labelKey")?.takeIf(String::isNotEmpty),
            level = (value["level"] as? JsonPrimitive)?.intOrNull,
            tone = tone(value) ?: return null,
            changes = (value["changes"] as? JsonArray ?: return null).map { change(it) ?: return null },
        )
    }

    private fun change(element: JsonElement): EffectChange? {
        val value = element as? JsonObject ?: return null
        val kind = value.text("kind")?.let { wire -> ChangeKind.entries.firstOrNull { it.wire == wire } } ?: return null
        return EffectChange(kind, (value["value"] as? JsonPrimitive)?.intOrNull, (value["included"] as? JsonPrimitive)?.booleanOrNull)
    }

    private fun tone(value: JsonObject): EffectTone? = value.text("tone")?.let { wire -> EffectTone.entries.firstOrNull { it.wire == wire } }

    private fun JsonObject.text(field: String): String? = (this[field] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
