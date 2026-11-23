package eu.junak.ttrpg.companion.core.model.character

import eu.junak.ttrpg.companion.core.model.HostFormatException
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** The six abilities, by the sheet's IDs. Their names are the app's text. */
enum class Ability {
    STR, DEX, CON, INT, WIS, CHA;

    companion object {
        fun of(id: String): Ability? = entries.firstOrNull { it.name == id }
    }
}

/** The skills, by the sheet's IDs. Their names are the app's text. */
enum class Skill(val id: String) {
    ACROBATICS("acrobatics"), ANIMAL_HANDLING("animalHandling"), ARCANA("arcana"), ATHLETICS("athletics"),
    DECEPTION("deception"), HISTORY("history"), INSIGHT("insight"), INTIMIDATION("intimidation"),
    INVESTIGATION("investigation"), MEDICINE("medicine"), NATURE("nature"), PERCEPTION("perception"),
    PERFORMANCE("performance"), PERSUASION("persuasion"), RELIGION("religion"), SLEIGHT_OF_HAND("sleightOfHand"),
    STEALTH("stealth"), SURVIVAL("survival");

    companion object {
        fun of(id: String): Skill? = entries.firstOrNull { it.id == id }
    }
}

enum class Proficiency { NONE, PROFICIENT, EXPERTISE }

/** What a resource is; its uses are spent through play changes or [CharacterInputs.setResourceSpent]. */
enum class ResourceKind(val id: String) {
    /** Uses or points of a feature, such as Lay on Hands or Channel Divinity. */
    POOL("pool"),

    /** A granted spell's free cast. */
    CHARGE("charge"),

    HIT_DICE("hitdice"),

    /** Spell slots, including pact slots. */
    SLOT("slot");

    companion object {
        fun of(id: String): ResourceKind? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What a character screen shows of one answer, read by hand from the saved
 * sheet (`state.projection`), the saved inputs where the sheet lacks a value,
 * and an evaluation's spell and condition options. Names and rules text are
 * the rules data's English; IDs the app does not know are kept as they are.
 * The character's own name is the campaign record's, not part of the answer.
 */
data class CharacterSheet(
    val header: SheetHeader,
    val combat: SheetCombat,
    val abilities: SheetAbilities,
    val spells: SheetSpells,
    val gear: SheetGear,
    val features: List<SheetFeature>,
    /** The conditions the "+ Condition" picker offers; empty without an evaluation. */
    val conditionOptions: List<ConditionOption>,
    val identity: SheetIdentity,
    /** The values temporary effects change, from the answer's own evaluation; empty without one. */
    val marks: List<ValueMark> = emptyList(),
) {
    companion object {
        /**
         * Reads [answer]'s saved sheet. Spell and condition options come from
         * [evaluation]: the answer's own, or for an answer without one (a
         * refused or conflicting change, a repeated operation) the last one
         * the caller has. A part that is absent reads as empty; a part of the
         * wrong shape is a [HostFormatException].
         */
        fun read(answer: CharacterAnswer, evaluation: CharacterEvaluation? = answer.evaluation): CharacterSheet {
            val state = requireNotNull(answer.state) { "the answer has no saved sheet" }
            return SheetReader(
                sheet = Node(state.projection.sheet, "sheet"),
                play = Node(state.inputs, "inputs").obj("play") ?: Node(JsonObject(emptyMap()), "inputs.play"),
                build = Node(state.inputs, "inputs").obj("build"),
                evidence = state.projection.evidence,
                options = evaluation?.let { Node(it.spellOptions, "spellOptions") },
                conditions = evaluation?.let { Node(it.guidance, "guidance").obj("conditions") },
            ).read().copy(marks = TemporaryEffects.read(answer.evaluation?.sheet))
        }
    }
}

data class SheetHeader(
    /** The species' name, such as "Human". */
    val species: String?,
    val classes: List<ClassLevel>,
    val hitPoints: Int,
    val maxHitPoints: Int,
    val temporaryHitPoints: Int,
    val armorClass: Int,
    val speed: Int,
    val initiative: Int,
    val inspiration: Boolean,
    val concentration: Concentration?,
    val conditions: List<ActiveCondition>,
)

data class ClassLevel(val classId: String, val name: String, val level: Int, val subclassId: String?, val subclassName: String?)

/** The spell being concentrated on; `end-concentration` ends it. */
data class Concentration(val ref: String, val name: String)

/** A condition on the character; [active] is false when it is immune or the condition does not apply. */
data class ActiveCondition(val id: String, val name: String, val level: Int, val active: Boolean)

data class SheetCombat(
    val attacks: List<Attack>,
    /** Pools and anything of an unknown kind, for [CharacterInputs.setResourceSpent]. */
    val limitedUses: List<LimitedUse>,
    /** Features switched on and off with `toggle-feature`, some spending a resource. */
    val toggles: List<FeatureToggle>,
    val hitDice: List<HitDice>,
)

data class Attack(
    val name: String,
    val toHit: Int,
    /** The damage roll, such as "1d8 +4"; with [damageType] it reads "1d8 +4 slashing". */
    val damage: String,
    val damageType: String,
    val versatileDamage: String?,
    val properties: List<String>,
    /** The weapon mastery's name, such as "Sap", when the weapon has one. */
    val mastery: String?,
    val masteryActive: Boolean,
    val range: AttackRange?,
    val reach: Int?,
    val itemId: String?,
    /** The Engine's stable key of the row (`character.AttackKey`), which its temporary effect marks use. */
    val key: String = itemId?.let { "item:$it" } ?: "",
)

data class AttackRange(val normal: Int, val long: Int, val thrown: Boolean)

/** A resource; [kindId] is the sheet's kind, [kind] null when the app does not know it. */
data class LimitedUse(
    val key: String,
    val name: String,
    val kindId: String,
    val kind: ResourceKind?,
    val maximum: Int,
    val left: Int,
    val spent: Int,
)

/** [resourceKey] is the resource switching it on spends from, such as Channel Divinity. */
data class FeatureToggle(val key: String, val name: String, val active: Boolean, val available: Boolean, val resourceKey: String?)

data class HitDice(val key: String, val die: String, val maximum: Int, val left: Int, val spent: Int)

data class SheetAbilities(
    /** The six abilities in their usual order, then any others the sheet has. */
    val abilities: List<AbilityRow>,
    val proficiencyBonus: Int,
    val passivePerception: Int,
    val skills: List<SkillRow>,
    /** Special senses such as darkvision, in the sheet's order; empty when it has none. */
    val senses: List<Sense> = emptyList(),
)

/** A special sense by the sheet's ID (`darkvision`, `devils-sight`) and its range in the ruleset's distance unit. */
data class Sense(val id: String, val range: Int)

data class AbilityRow(
    val id: String,
    val ability: Ability?,
    val score: Int,
    val modifier: Int,
    val save: Int,
    val saveProficient: Boolean,
)

/** A skill; [disadvantage] marks, for example, heavy armour on Stealth. */
data class SkillRow(
    val id: String,
    val skill: Skill?,
    val abilityId: String,
    val bonus: Int,
    val proficiency: Proficiency,
    val disadvantage: Boolean,
)

data class SheetSpells(
    val casters: List<SpellCaster>,
    /** Spell slots by level, and pact slots. */
    val slots: List<SpellSlot>,
    /** The spells the character can cast now, by level and name; empty without an evaluation. */
    val spells: List<CastableSpell>,
)

/** A casting class; [preparedCount] and [preparedLimit] are null when the sheet does not say them. */
data class SpellCaster(
    val classId: String,
    val name: String,
    val level: Int,
    val saveDc: Int,
    val attackBonus: Int,
    val preparedCount: Int? = null,
    val preparedLimit: Int? = null,
)

/** A slot pool such as `slot-1` or `pact-slot`. */
data class SpellSlot(val key: String, val level: Int, val maximum: Int, val left: Int, val pact: Boolean)

/**
 * A spell the character can cast, with the facts the cast sheet shows (from
 * the rules data's evidence) and every way to cast it.
 */
data class CastableSpell(
    val ref: String,
    val name: String,
    /** 0 for a cantrip. */
    val level: Int,
    val castingTime: String?,
    val range: String?,
    val duration: String?,
    val school: String?,
    val summary: String?,
    val concentration: Boolean,
    val ritual: Boolean,
    /** `cast-spell` through a class, with one of its slot keys ("" casts without a slot). */
    val classes: List<ClassCasting>,
    /** The classes that can cast it as a ritual (`cast-ritual`), without a slot. */
    val ritualClasses: List<String>,
    /** `cast-granted-spell`: spells granted by a feature, feat or species, some with free uses. */
    val grants: List<GrantedCasting>,
)

data class ClassCasting(val classId: String, val slots: List<String>)

data class GrantedCasting(
    val key: String,
    val slots: List<String>,
    /** The free uses as the rules word them, such as "1/long". */
    val free: String?,
    /** The resource holding the free uses, such as "1 free" left. */
    val freeUses: LimitedUse?,
    /** Cast without a slot at its own level ("" among [slots]); slots still cast it higher. */
    val atWill: Boolean,
)

data class SheetGear(
    /** Items pinned for quick use; `consume-item` uses one. */
    val quickUse: List<QuickUseItem>,
    val mainHand: HeldItem?,
    val offHand: HeldItem?,
    /** Coins by kind: pp, gp, ep, sp, cp, then any other kind. */
    val coins: List<Coins>,
    /** The carried weight and what the character can carry; null when the sheet does not say them. */
    val carriedPounds: Double?,
    val capacityPounds: Double?,
    /** Every inventory item as saved, with the container it is in. */
    val items: List<GearItem>,
    /** Attuned magic items and how many the character may attune; null when the sheet does not say. */
    val attunement: Attunement? = null,
)

data class Attunement(val count: Int, val limit: Int)

/**
 * Who the character is, as far as the answer says: [size] as the rules data
 * words it ("Medium"), the [background]'s name from the answer's evidence,
 * and the [languages]. Null or empty for what the answer does not carry.
 */
data class SheetIdentity(val size: String?, val background: String?, val languages: List<String>)

data class QuickUseItem(val itemId: String, val name: String, val quantity: Int, val available: Boolean, val location: String?, val container: String?)

data class HeldItem(val itemId: String, val name: String, val active: Boolean)

data class Coins(val coin: String, val amount: Long)

data class GearItem(val id: String, val name: String, val quantity: Int, val location: String, val container: String?)

/** A feature and where it comes from, such as "Paladin 1" or "Oath of Devotion 3". */
data class SheetFeature(val id: String, val name: String, val source: FeatureSource)

/** [name] is the source's name when the answer has it (a class, subclass, species, background or feat). */
data class FeatureSource(val type: String, val id: String?, val name: String?, val level: Int?)

data class ConditionOption(val id: String, val name: String, val maximumLevel: Int, val canAdd: Boolean)

private const val BOUNDARY = "dnd5e.character sheet"

private fun fail(path: String, problem: String): Nothing = throw HostFormatException(BOUNDARY, "$path $problem")

/** A JSON object at [path]: absent members read as null or empty; a member of the wrong type is a format error. */
private class Node(val json: JsonObject, val path: String) {
    private fun present(field: String): JsonElement? = json[field]?.takeUnless { it is JsonNull }

    fun obj(field: String): Node? = present(field)?.let { it as? JsonObject ?: fail("$path.$field", "must be an object") }
        ?.let { Node(it, "$path.$field") }

    fun objects(field: String): List<Node> = array(field).mapIndexed { index, value ->
        Node(value as? JsonObject ?: fail("$path.$field[$index]", "must be an object"), "$path.$field[$index]")
    }

    /** The members of an object keyed by ID, such as abilities or skills. */
    fun members(field: String): List<Pair<String, Node>> = obj(field)?.json?.map { (key, value) ->
        key to Node(value as? JsonObject ?: fail("$path.$field.$key", "must be an object"), "$path.$field.$key")
    }.orEmpty()

    fun array(field: String): JsonArray = present(field)?.let { it as? JsonArray ?: fail("$path.$field", "must be an array") }
        ?: JsonArray(emptyList())

    fun strings(field: String): List<String> = array(field).mapIndexed { index, value ->
        (value as? JsonPrimitive)?.takeIf { it.isString }?.content ?: fail("$path.$field[$index]", "must be text")
    }

    fun string(field: String): String? = present(field)?.let {
        (it as? JsonPrimitive)?.takeIf { primitive -> primitive.isString }?.content ?: fail("$path.$field", "must be text")
    }

    fun int(field: String): Int? = present(field)?.let { (it.number(field))?.intOrNull ?: fail("$path.$field", "must be a whole number") }

    fun long(field: String): Long? = present(field)?.let { (it.number(field))?.longOrNull ?: fail("$path.$field", "must be a whole number") }

    fun double(field: String): Double? = present(field)?.let { (it.number(field))?.doubleOrNull ?: fail("$path.$field", "must be a number") }

    fun bool(field: String): Boolean? = present(field)?.let {
        (it as? JsonPrimitive)?.takeIf { primitive -> !primitive.isString }?.booleanOrNull ?: fail("$path.$field", "must be true or false")
    }

    fun requireString(field: String): String = string(field) ?: fail("$path.$field", "is missing")

    fun requireInt(field: String): Int = int(field) ?: fail("$path.$field", "is missing")

    private fun JsonElement.number(field: String): JsonPrimitive? =
        (this as? JsonPrimitive)?.takeIf { !it.isString } ?: fail("$path.$field", "must be a number")
}

/** An attack row's stable key, as the Engine forms it (`character.AttackKey`). */
private fun attackKey(row: Node): String = row.string("itemId")?.takeIf(String::isNotEmpty)?.let { "item:$it" }
    ?: row.string("featureAttack")?.takeIf(String::isNotEmpty)?.let { "feature:$it" }
    ?: "unarmed".takeIf { row.bool("unarmed") == true }
    ?: "ref:" + (row.string("ref") ?: "")

private class SheetReader(
    private val sheet: Node,
    private val play: Node,
    private val build: Node?,
    private val evidence: JsonArray,
    private val options: Node?,
    private val conditions: Node?,
) {
    private val derived = sheet.obj("derived")

    /** Evidence entries by kind and ID: names of classes, subclasses and the like, and spell facts. */
    private val records: Map<Pair<String, String>, Node> = evidence.mapIndexed { index, value ->
        Node(value as? JsonObject ?: fail("evidence[$index]", "must be an object"), "evidence[$index]")
    }.mapNotNull { entry ->
        val reference = entry.obj("reference") ?: return@mapNotNull null
        (reference.requireString("kind") to reference.requireString("id")) to entry
    }.toMap()

    private fun recordName(kind: String, id: String): String? = records[kind to id]?.string("name")

    private val classes: List<ClassLevel> = sheet.objects("classes").map {
        val id = it.requireString("classId")
        val subclass = it.string("subclass")?.takeIf(String::isNotEmpty)
        ClassLevel(id, it.string("name") ?: id, it.int("level") ?: 0, subclass, subclass?.let { s -> recordName("subclass", s) })
    }

    private val resources: List<LimitedUse> = sheet.objects("resources").map {
        val key = it.requireString("key")
        val kind = it.requireString("kind")
        LimitedUse(key, it.string("name") ?: key, kind, ResourceKind.of(kind), it.requireInt("max"), it.requireInt("remaining"), it.requireInt("spent"))
    }

    /** The sheet's containers, with their names and the IDs of the items inside. */
    private val containers: List<Node> = sheet.obj("storage")?.objects("containers").orEmpty()

    private fun containerOfItem(itemId: String): String? =
        containers.firstOrNull { itemId in it.strings("itemIds") }?.let { it.string("name") ?: it.requireString("id") }

    private fun containerName(containerId: String?): String? = containerId?.takeIf(String::isNotEmpty)?.let { id ->
        containers.firstOrNull { it.string("id") == id }?.string("name") ?: id
    }

    fun read(): CharacterSheet = CharacterSheet(
        header = header(),
        combat = combat(),
        abilities = abilities(),
        spells = spells(),
        gear = gear(),
        features = sheet.objects("features").map {
            val id = it.requireString("id")
            val source = it.obj("source")
            val type = source?.string("type") ?: ""
            val sourceId = source?.string("id")
            val sourceName = sourceId?.let { s -> classes.firstOrNull { c -> type == "class" && c.classId == s }?.name ?: recordName(type, s) }
            SheetFeature(id, it.string("name") ?: id, FeatureSource(type, sourceId, sourceName, source?.int("level")))
        },
        conditionOptions = conditions?.objects("options").orEmpty().map {
            val id = it.requireString("id")
            ConditionOption(id, it.string("name") ?: id, it.int("maximumLevel") ?: 1, it.bool("canAdd") ?: true)
        },
        identity = SheetIdentity(
            size = derived?.string("size")?.takeIf(String::isNotEmpty),
            background = build?.string("background")?.takeIf(String::isNotEmpty)?.let { recordName("background", it) },
            languages = sheet.strings("languages").filter(String::isNotEmpty),
        ),
    )

    private fun header() = SheetHeader(
        species = sheet.obj("species")?.let { it.string("name") ?: it.string("id") },
        classes = classes,
        hitPoints = play.int("hp") ?: 0,
        maxHitPoints = derived?.int("maxHp") ?: 0,
        temporaryHitPoints = play.int("temporaryHp") ?: 0,
        armorClass = derived?.int("armorClass") ?: 0,
        speed = derived?.int("speed") ?: 0,
        initiative = derived?.int("initiative") ?: 0,
        inspiration = sheet.bool("inspiration") ?: false,
        concentration = sheet.obj("concentration")?.let { Concentration(it.requireString("ref"), it.requireString("name")) },
        conditions = sheet.objects("conditions").map {
            val id = it.requireString("id")
            ActiveCondition(id, it.string("name") ?: id, it.int("level") ?: 1, (it.string("status") ?: "active") == "active")
        },
    )

    private fun combat() = SheetCombat(
        attacks = sheet.objects("weapons").map {
            val range = it.obj("range")
            Attack(
                name = it.requireString("name"),
                toHit = it.int("attackBonus") ?: 0,
                damage = it.string("damage") ?: "",
                damageType = it.string("damageType") ?: "",
                versatileDamage = it.string("versatileDamage"),
                properties = it.strings("properties"),
                mastery = it.string("mastery")?.takeIf(String::isNotEmpty),
                masteryActive = it.bool("masteryActive") ?: false,
                range = range?.let { r -> AttackRange(r.int("normal") ?: 0, r.int("long") ?: 0, r.bool("thrown") ?: false) },
                reach = it.int("reach"),
                itemId = it.string("itemId"),
                key = attackKey(it),
            )
        },
        limitedUses = resources.filter { it.kind == ResourceKind.POOL || it.kind == null },
        toggles = sheet.objects("activations").map {
            val key = it.requireString("key")
            val resource = it.obj("resource")
            FeatureToggle(key, it.string("name") ?: key, it.bool("active") ?: false, it.bool("available") ?: true, resource?.string("key"))
        },
        hitDice = sheet.objects("resources").filter { it.string("kind") == ResourceKind.HIT_DICE.id }.map {
            val use = resources.first { use -> use.key == it.requireString("key") }
            HitDice(use.key, it.string("die") ?: "", use.maximum, use.left, use.spent)
        },
    )

    private fun abilities(): SheetAbilities {
        val saves = sheet.members("saves").toMap()
        val rows = sheet.members("abilities").map { (id, score) ->
            val save = saves[id]
            AbilityRow(
                id = id,
                ability = Ability.of(id),
                score = score.int("score") ?: 0,
                modifier = score.int("mod") ?: 0,
                save = save?.int("total") ?: score.int("mod") ?: 0,
                saveProficient = save?.bool("proficient") ?: false,
            )
        }
        return SheetAbilities(
            abilities = rows.sortedBy { it.ability?.ordinal ?: Ability.entries.size },
            proficiencyBonus = derived?.int("proficiencyBonus") ?: 0,
            passivePerception = derived?.int("passivePerception") ?: 0,
            senses = sheet.obj("senses")?.json?.keys.orEmpty().map { id -> Sense(id, sheet.obj("senses")?.int(id) ?: 0) },
            skills = sheet.members("skills").map { (id, skill) ->
                SkillRow(
                    id = id,
                    skill = Skill.of(id),
                    abilityId = skill.string("ability") ?: "",
                    bonus = skill.int("total") ?: 0,
                    proficiency = when {
                        skill.bool("expertise") == true -> Proficiency.EXPERTISE
                        skill.bool("proficient") == true -> Proficiency.PROFICIENT
                        else -> Proficiency.NONE
                    },
                    disadvantage = skill.bool("disadvantage") ?: false,
                )
            },
        )
    }

    private fun spells(): SheetSpells {
        val spellcasting = sheet.obj("spellcasting")
        val slots = sheet.objects("resources").filter { it.string("kind") == ResourceKind.SLOT.id }.map {
            val use = resources.first { use -> use.key == it.requireString("key") }
            SpellSlot(use.key, it.int("level") ?: 0, use.maximum, use.left, it.obj("source")?.string("type") == "pactMagic")
        }
        val byRef = linkedMapOf<String, CastableSpell>()
        /** The spell's entry, with its facts from the evidence; [name] and [level] stand in when there is none. */
        fun spell(ref: String, name: String? = null, level: Int? = null): CastableSpell = byRef.getOrPut(ref) {
            val record = records["spell" to ref]
            val facts = record?.obj("facts")
            CastableSpell(
                ref = ref,
                name = record?.string("name") ?: facts?.string("name") ?: name ?: ref,
                level = facts?.int("level") ?: level ?: 0,
                castingTime = facts?.string("castingTime"),
                range = facts?.string("range"),
                duration = facts?.string("duration"),
                school = facts?.string("school"),
                summary = record?.string("summary"),
                concentration = facts?.bool("concentration") ?: false,
                ritual = facts?.bool("ritual") ?: false,
                classes = emptyList(),
                ritualClasses = emptyList(),
                grants = emptyList(),
            )
        }
        for (caster in options?.objects("classes").orEmpty()) {
            val classId = caster.requireString("classId")
            for ((ref, castSlots) in caster.obj("castSlots")?.json.orEmpty()) {
                val keys = (castSlots as? JsonArray ?: fail("${caster.path}.castSlots.$ref", "must be an array")).map {
                    (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content ?: fail("${caster.path}.castSlots.$ref", "must hold text")
                }
                byRef[ref] = spell(ref).let { it.copy(classes = it.classes + ClassCasting(classId, keys)) }
            }
            for (ref in caster.strings("ritualIds")) byRef[ref] = spell(ref).let { it.copy(ritualClasses = it.ritualClasses + classId) }
        }
        for (grant in options?.objects("granted").orEmpty()) {
            val ref = grant.requireString("ref")
            val keys = grant.strings("slots")
            val casting = GrantedCasting(
                key = grant.requireString("key"),
                slots = keys,
                free = grant.string("free"),
                freeUses = resources.firstOrNull { it.kind == ResourceKind.CHARGE && it.key in keys },
                atWill = grant.bool("atWill") ?: false,
            )
            byRef[ref] = spell(ref, grant.string("name"), grant.int("level")).let { it.copy(grants = it.grants + casting) }
        }
        return SheetSpells(
            casters = spellcasting?.objects("perClass").orEmpty().map {
                val id = it.requireString("classId")
                SpellCaster(
                    id,
                    classes.firstOrNull { c -> c.classId == id }?.name ?: id,
                    it.int("level") ?: 0,
                    it.int("saveDC") ?: 0,
                    it.int("spellAttack") ?: 0,
                    it.int("preparedCount"),
                    it.int("preparedLimit"),
                )
            },
            slots = slots,
            spells = byRef.values.sortedWith(compareBy({ it.level }, { it.name })),
        )
    }

    private fun gear(): SheetGear {
        val hands = sheet.obj("hands")
        fun held(side: String): HeldItem? = hands?.obj(side)?.let {
            val itemId = it.string("itemId") ?: ""
            if (itemId.isEmpty()) null else HeldItem(itemId, it.string("name") ?: itemId, it.bool("active") ?: false)
        }
        val currency = play.obj("currency")?.let { node -> node.json.keys.associateWith { node.long(it) ?: 0L } }.orEmpty()
        val order = listOf("pp", "gp", "ep", "sp", "cp")
        val carrying = sheet.obj("carrying")
        return SheetGear(
            quickUse = sheet.objects("quickUse").map {
                val itemId = it.requireString("itemId")
                QuickUseItem(itemId, it.string("name") ?: itemId, it.int("quantity") ?: 0, it.bool("available") ?: false, it.string("location"), containerOfItem(itemId))
            },
            mainHand = held("main"),
            offHand = held("off"),
            coins = currency.entries.sortedBy { (coin, _) -> order.indexOf(coin).let { if (it < 0) order.size else it } }.map { Coins(it.key, it.value) },
            carriedPounds = carrying?.double("weightLb"),
            capacityPounds = carrying?.double("capacityLb"),
            items = play.objects("inventory").map {
                val id = it.requireString("id")
                GearItem(id, it.string("name") ?: id, it.int("quantity") ?: 1, it.string("location") ?: "", containerName(it.string("containerId")))
            },
            attunement = sheet.obj("attunement")?.let { Attunement(it.int("count") ?: 0, it.requireInt("limit")) },
        )
    }
}
