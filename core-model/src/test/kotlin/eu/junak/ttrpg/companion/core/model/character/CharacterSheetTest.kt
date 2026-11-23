package eu.junak.ttrpg.companion.core.model.character

import eu.junak.ttrpg.companion.core.model.HostFormatException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The sheet view read from the recordings: Bren Ashcroft (the mockup's
 * paladin), Corin Vale (a warlock with pact slots) and Mira Quill (a wizard).
 */
class CharacterSheetTest {

    private val placeholder = "Placeholder: compendium text removed."

    private fun answer(name: String) = Recordings.read(name).answer()

    private fun sheet(name: String) = CharacterSheet.read(answer(name))

    private val bren by lazy { sheet("sheet-bren-load") }

    @Test
    fun theHeaderShowsBrenAsTheMockupDoes() {
        assertEquals(
            SheetHeader(
                species = "Human",
                classes = listOf(ClassLevel("paladin", "Paladin", 5, "oath-of-devotion", "Oath of Devotion")),
                hitPoints = 31,
                maxHitPoints = 44,
                temporaryHitPoints = 0,
                armorClass = 19,
                speed = 30,
                initiative = 3,
                inspiration = true,
                concentration = Concentration("bless", "Bless"),
                conditions = listOf(ActiveCondition("poisoned", "Poisoned", 1, active = true)),
            ),
            bren.header,
        )
    }

    @Test
    fun combatHasAttacksLimitedUsesTogglesAndHitDice() {
        assertEquals(
            listOf(
                Attack("Unarmed Strike", 7, "5", "bludgeoning", null, emptyList(), null, false, null, 5, null),
                Attack("Longsword", 7, "1d8 +4", "slashing", "1d10 +4", listOf("versatile"), "Sap", true, null, 5, "sword"),
            ),
            bren.combat.attacks,
        )
        assertEquals(
            listOf(
                LimitedUse("lay-on-hands", "Lay on Hands", "pool", ResourceKind.POOL, 25, 25, 0),
                LimitedUse("channel-divinity", "Channel Divinity", "pool", ResourceKind.POOL, 2, 2, 0),
            ),
            bren.combat.limitedUses,
        )
        assertEquals(
            listOf(FeatureToggle("feature:oath-of-devotion-sacred-weapon:sacred-weapon", "Sacred Weapon", false, true, "channel-divinity")),
            bren.combat.toggles,
        )
        assertEquals(listOf(HitDice("hit-dice-d10", "d10", 5, 4, 1)), bren.combat.hitDice)
    }

    @Test
    fun aToggledFeatureIsActiveAndSpendsItsResource() {
        val on = sheet("sheet-bren-save-toggle-on").combat
        assertTrue(on.toggles.single().active)
        assertEquals(LimitedUse("channel-divinity", "Channel Divinity", "pool", ResourceKind.POOL, 2, 1, 1), on.limitedUses[1])
    }

    @Test
    fun abilitiesSavesAndSkills() {
        val abilities = bren.abilities
        assertEquals(Ability.entries, abilities.abilities.map { it.ability })
        assertEquals(AbilityRow("STR", Ability.STR, 18, 4, 4, false), abilities.abilities[0])
        assertEquals(AbilityRow("WIS", Ability.WIS, 12, 1, 4, true), abilities.abilities[4])
        assertEquals(AbilityRow("CHA", Ability.CHA, 15, 2, 5, true), abilities.abilities[5])
        assertEquals(3, abilities.proficiencyBonus)
        assertEquals(11, abilities.passivePerception)
        assertEquals(Skill.entries.toSet(), abilities.skills.map { it.skill }.toSet())
        assertEquals(SkillRow("athletics", Skill.ATHLETICS, "STR", 7, Proficiency.PROFICIENT, true), abilities.skills.first { it.id == "athletics" })

        // Before he was poisoned, only his chain mail gave a disadvantage: on Stealth.
        val unpoisoned = sheet("sheet-bren-save-toggle-on").abilities.skills
        assertEquals(listOf("stealth"), unpoisoned.filter { it.disadvantage }.map { it.id })
        assertEquals(SkillRow("arcana", Skill.ARCANA, "INT", 10, Proficiency.EXPERTISE, false), sheet("load-sheet").abilities.skills.first { it.id == "arcana" })
    }

    @Test
    fun spellsListEveryWayToCastThem() {
        val spells = bren.spells
        assertEquals(listOf(SpellCaster("paladin", "Paladin", 5, 13, 5, preparedCount = 6, preparedLimit = 6)), spells.casters)
        assertEquals(listOf(SpellSlot("slot-1", 1, 4, 3, false), SpellSlot("slot-2", 2, 2, 2, false)), spells.slots)
        assertEquals(
            listOf(
                "Bless", "Command", "Cure Wounds", "Divine Smite", "Heroism", "Protection from Evil and Good", "Shield of Faith",
                "Aid", "Find Steed", "Zone of Truth",
            ),
            spells.spells.map { it.name },
        )
        assertEquals(
            CastableSpell(
                ref = "bless", name = "Bless", level = 1, castingTime = "Action", range = "30 feet",
                duration = "Concentration, up to 1 minute-", school = "Enchantment", summary = placeholder,
                concentration = true, ritual = false,
                classes = listOf(ClassCasting("paladin", listOf("slot-1", "slot-2"))), ritualClasses = emptyList(), grants = emptyList(),
            ),
            spells.spells.first { it.ref == "bless" },
        )
        val steed = spells.spells.first { it.ref == "find-steed" }
        val charge = "charge:feature:paladin-faithful-steed:find-steed"
        assertEquals(listOf(ClassCasting("paladin", listOf("slot-2"))), steed.classes)
        assertEquals(
            listOf(
                GrantedCasting(
                    "feature:paladin-faithful-steed:find-steed", listOf(charge, "slot-2"), "1/long",
                    LimitedUse(charge, "Find Steed (free cast)", "charge", ResourceKind.CHARGE, 1, 1, 0), atWill = false,
                ),
            ),
            steed.grants,
        )
        val smite = spells.spells.first { it.ref == "divine-smite" }
        assertEquals("cast only as granted", emptyList<ClassCasting>(), smite.classes)
        assertEquals(placeholder, smite.castingTime)
        assertEquals(0, sheet("sheet-bren-save-cast-granted").spells.spells.first { it.ref == "find-steed" }.grants.single().freeUses!!.left)
    }

    @Test
    fun pactSlotsCantripsAndAtWillGrants() {
        val corin = sheet("sheet-corin-load")
        assertEquals(listOf(SpellSlot("pact-slot", 1, 2, 2, true)), corin.spells.slots)
        assertEquals(listOf(SpellCaster("warlock", "Warlock", 2, 12, 4, preparedCount = 3, preparedLimit = 3)), corin.spells.casters)
        val blast = corin.spells.spells.first { it.ref == "eldritch-blast" }
        assertEquals(0, blast.level)
        assertEquals(listOf(ClassCasting("warlock", listOf(""))), blast.classes)
        val armor = corin.spells.spells.first { it.ref == "mage-armor" }.grants.single()
        assertTrue(armor.atWill)
        assertEquals(listOf("", "pact-slot"), armor.slots)
        assertEquals(1, corin.spells.spells.first { it.ref == "burning-hands" }.grants.single().freeUses!!.left)
        assertEquals(
            Attack("Dagger", 4, "1d4 +2", "piercing", null, listOf("finesse", "light", "thrown"), "Nick", false, AttackRange(20, 60, true), 5, "dagger"),
            corin.combat.attacks.last(),
        )
    }

    @Test
    fun ritualsComeFromTheSpellbook() {
        val mira = sheet("load-sheet").spells.spells
        val familiar = mira.first { it.ref == "find-familiar" }
        assertEquals(listOf("wizard"), familiar.ritualClasses)
        assertTrue("prepared spells only", familiar.classes.isEmpty())
        assertTrue(familiar.ritual)
        val detect = mira.first { it.ref == "detect-magic" }
        assertEquals(listOf("wizard"), detect.ritualClasses)
        assertEquals(listOf("wizard"), detect.classes.map { it.classId })
        assertEquals("charge:species:elf:detect-magic", detect.grants.single().freeUses!!.key)
    }

    @Test
    fun gearHasQuickUseHandsCoinsWeightAndItems() {
        val gear = bren.gear
        assertEquals(listOf(QuickUseItem("potions", "Potion of Healing", 2, true, "carried", "Belt pouch")), gear.quickUse)
        assertEquals(HeldItem("sword", "Longsword", true), gear.mainHand)
        assertEquals(HeldItem("shield", "Shield", true), gear.offHand)
        assertEquals(listOf(Coins("pp", 2), Coins("gp", 87), Coins("ep", 0), Coins("sp", 30), Coins("cp", 12)), gear.coins)
        assertEquals(97.62, gear.carriedPounds!!, 0.001)
        assertEquals(270.0, gear.capacityPounds!!, 0.001)
        assertEquals(GearItem("rations", "Rations", 6, "carried", "Backpack"), gear.items.first { it.id == "rations" })
        assertEquals(GearItem("javelins", "Javelin", 4, "carried", null), gear.items.first { it.id == "javelins" })
        assertNull(sheet("sheet-corin-load").gear.offHand)
    }

    @Test
    fun theGlanceValuesComeFromTheAnswer() {
        val mira = sheet("load-sheet")
        assertEquals(listOf(Sense("darkvision", 60)), mira.abilities.senses)
        assertEquals(Attunement(count = 0, limit = 3), mira.gear.attunement)
        assertEquals(9 to 9, mira.spells.casters.single().let { it.preparedCount to it.preparedLimit })
        // The background's name comes from the answer's evidence; the languages list is there but empty.
        assertEquals(SheetIdentity(size = "Medium", background = "Sage", languages = emptyList()), mira.identity)

        // The trimmed recordings lack these parts: nothing is made up for them.
        assertEquals(emptyList<Sense>(), bren.abilities.senses)
        assertNull(bren.gear.attunement)
    }

    @Test
    fun featuresSayWhereTheyComeFrom() {
        assertEquals(SheetFeature("paladin-lay-on-hands", "Lay on Hands", FeatureSource("class", "paladin", "Paladin", 1)), bren.features.first())
        assertEquals(
            SheetFeature("oath-of-devotion-sacred-weapon", "Sacred Weapon", FeatureSource("subclass", "oath-of-devotion", "Oath of Devotion", 3)),
            bren.features.last(),
        )
    }

    @Test
    fun conditionOptionsComeFromTheEvaluation() {
        assertEquals(15, bren.conditionOptions.size)
        assertEquals(ConditionOption("blinded", "Blinded", 1, true), bren.conditionOptions.first())
        assertEquals(6, bren.conditionOptions.first { it.id == "exhaustion" }.maximumLevel)
    }

    @Test
    fun anAnswerWithoutEvaluationKeepsTheLastOptions() {
        val refused = answer("sheet-bren-save-refused")
        assertNull(refused.evaluation)
        val alone = CharacterSheet.read(refused)
        assertTrue(alone.spells.spells.isEmpty() && alone.conditionOptions.isEmpty())
        assertEquals("the saved sheet still reads", bren.header, alone.header)

        val withLast = CharacterSheet.read(refused, answer("sheet-bren-load").evaluation)
        assertEquals(bren, withLast)
    }

    @Test
    fun unknownFieldsAndIdsAreKept() {
        val read = CharacterSheet.read(
            edited("sheet-bren-load") { sheet ->
                sheet + mapOf(
                    "newPart" to JsonPrimitive(1),
                    "abilities" to JsonObject(sheet.getValue("abilities").jsonObject + ("LCK" to json("""{"score":11,"mod":0,"base":11,"bonus":0}"""))),
                    "skills" to JsonObject(sheet.getValue("skills").jsonObject + ("astrology" to json("""{"ability":"INT","mod":-1,"total":-1,"proficient":false,"expertise":false,"glow":true}"""))),
                    "resources" to JsonArray(sheet.getValue("resources") as JsonArray + json("""{"key":"rage","name":"Rage","kind":"fury","max":3,"remaining":3,"spent":0,"recharge":[],"source":{"type":"class"}}""")),
                    "conditions" to JsonArray(listOf(json("""{"id":"dazed","name":"Dazed","level":1,"status":"active","summary":"x","aura":"red"}"""))),
                )
            },
        )
        assertEquals(AbilityRow("LCK", null, 11, 0, 0, false), read.abilities.abilities.last())
        assertEquals(SkillRow("astrology", null, "INT", -1, Proficiency.NONE, false), read.abilities.skills.last())
        assertEquals(LimitedUse("rage", "Rage", "fury", null, 3, 3, 0), read.combat.limitedUses.last())
        assertEquals(listOf(ActiveCondition("dazed", "Dazed", 1, true)), read.header.conditions)
    }

    @Test
    fun absentPartsAreEmpty() {
        val read = CharacterSheet.read(
            edited("sheet-bren-load") { it - setOf("weapons", "resources", "activations", "spellcasting", "storage", "features", "quickUse", "concentration") },
        )
        assertTrue(read.combat.attacks.isEmpty() && read.combat.limitedUses.isEmpty() && read.combat.hitDice.isEmpty())
        assertTrue(read.spells.casters.isEmpty() && read.spells.slots.isEmpty() && read.features.isEmpty() && read.gear.quickUse.isEmpty())
        assertNull(read.header.concentration)
        assertEquals("Backpack is only a container name the sheet has", "backpack", read.gear.items.first { it.id == "rations" }.container)
    }

    @Test
    fun aPartOfTheWrongShapeIsAFormatError() {
        val broken = listOf<(JsonObject) -> Map<String, JsonElement>>(
            { it + ("weapons" to JsonObject(emptyMap())) },
            { it + ("skills" to JsonObject(mapOf("stealth" to JsonPrimitive(3)))) },
            { it + ("resources" to JsonArray(listOf(json("""{"key":"x","name":"X","kind":"pool","max":"2","remaining":2,"spent":0}""")))) },
            { it + ("resources" to JsonArray(listOf(json("""{"name":"X","kind":"pool","max":2,"remaining":2,"spent":0}""")))) },
            { it + ("inspiration" to JsonPrimitive("yes")) },
            { it + ("hands" to json("""{"main":"sword","off":{},"grip":"one"}""")) },
        )
        for (edit in broken) assertThrows(HostFormatException::class.java) { CharacterSheet.read(edited("sheet-bren-load", edit)) }
        val badSlots = Recordings.read("sheet-bren-load").result.jsonObject.let { result ->
            val evaluation = result.getValue("evaluation").jsonObject
            val options = evaluation.getValue("spellOptions").jsonObject
            val caster = (options.getValue("classes") as JsonArray).first().jsonObject
            val broken = JsonObject(caster + ("castSlots" to json("""{"bless":"slot-1"}""")))
            JsonObject(result + ("evaluation" to JsonObject(evaluation + ("spellOptions" to JsonObject(options + ("classes" to JsonArray(listOf(broken))))))))
        }
        assertThrows(HostFormatException::class.java) { CharacterSheet.read(CharacterAnswer.parse(badSlots, "bren-ashcroft")) }
    }

    /** Every field the view reads is declared in the pinned schemas. */
    @Test
    fun theViewReadsOnlyDeclaredFields() {
        val response = Recordings.responseSchema().getValue("\$defs").jsonObject
        val request = Recordings.schema().getValue("\$defs").jsonObject
        val read = mapOf(
            response to mapOf(
                "Sheet" to listOf("species", "classes", "derived", "inspiration", "concentration", "conditions", "weapons", "resources", "activations", "abilities", "saves", "skills", "spellcasting", "quickUse", "hands", "storage", "carrying", "features"),
                "RecordIdentity" to listOf("id", "name"),
                "SheetClass" to listOf("classId", "name", "level", "subclass"),
                "Derived" to listOf("maxHp", "armorClass", "speed", "initiative", "proficiencyBonus", "passivePerception"),
                "SheetConcentration" to listOf("ref", "name"),
                "SheetCondition" to listOf("id", "name", "level", "status"),
                "Attack" to listOf("name", "attackBonus", "damage", "damageType", "versatileDamage", "properties", "mastery", "masteryActive", "range", "reach", "itemId"),
                "WeaponRange" to listOf("normal", "long", "thrown"),
                "Resource" to listOf("key", "name", "kind", "max", "remaining", "spent", "die", "level", "source"),
                "Source" to listOf("type", "id", "level"),
                "Activation" to listOf("key", "name", "active", "available", "resource"),
                "ActivationResource" to listOf("key"),
                "AbilityScore" to listOf("score", "mod"),
                "SaveRow" to listOf("total", "proficient"),
                "SkillRow" to listOf("ability", "total", "proficient", "expertise", "disadvantage"),
                "Spellcasting" to listOf("perClass"),
                "Caster" to listOf("classId", "level", "saveDC", "spellAttack"),
                "QuickUsePin" to listOf("itemId", "name", "quantity", "available", "location"),
                "SheetHands" to listOf("main", "off"),
                "HandSlot" to listOf("itemId", "name", "active"),
                "SheetStorage" to listOf("containers"),
                "StorageContainer" to listOf("id", "name", "itemIds"),
                "Carrying" to listOf("weightLb", "capacityLb"),
                "SheetFeature" to listOf("id", "name", "source"),
                "Evidence" to listOf("reference", "name", "summary", "facts"),
                "Reference" to listOf("kind", "id"),
                "Result" to listOf("spellOptions", "guidance"),
                "SpellOptions" to listOf("classes", "granted"),
                "CasterOptions" to listOf("classId", "castSlots", "ritualIds"),
                "SpellGrantOptions" to listOf("ref", "key", "name", "level", "slots", "free", "atWill"),
                "Guidance" to listOf("conditions"),
                "ConditionGuidance" to listOf("options"),
                "ConditionOption" to listOf("id", "name", "maximumLevel", "canAdd"),
            ),
            request to mapOf(
                "Inputs" to listOf("play"),
                "Play" to listOf("hp", "temporaryHp", "currency", "inventory"),
                "Item" to listOf("id", "name", "quantity", "location", "containerId"),
            ),
        )
        for ((defs, types) in read) {
            for ((type, fields) in types) {
                val declared = defs.getValue(type).jsonObject.getValue("properties").jsonObject.keys
                assertTrue("$type lacks ${fields - declared}", declared.containsAll(fields))
            }
        }
    }

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    /** [name]'s answer with its saved sheet changed by [edit]. */
    private fun edited(name: String, edit: (JsonObject) -> Map<String, JsonElement>): CharacterAnswer {
        val result = Recordings.read(name).result.jsonObject
        val state = result.getValue("state").jsonObject
        val projection = state.getValue("projection").jsonObject
        val sheet = JsonObject(edit(projection.getValue("sheet").jsonObject))
        val changed: JsonElement = JsonObject(result + ("state" to JsonObject(state + ("projection" to JsonObject(projection + ("sheet" to sheet))))))
        return CharacterAnswer.parse(changed, result.getValue("key").let { (it as JsonPrimitive).content })
    }
}
