package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

/** A link to a visible record, with what a chip or row needs to show it. */
data class RecordRef(
    val page: CampaignPage,
    val key: String,
    val name: String,
    val portrait: String?,
    val icon: String = "",
)

/** Which structured field a [Fact] shows; the app names each one in the reader's language. */
enum class FactKind {
    STATUS, TITLE, RANK, SPECIES, GENDER, AGE, FACTION, LOCATION,
    TYPE, REGION, PARENT, MARKER, DATE, SESSION, PRIORITY, CONSEQUENCE,
    DOMAIN, ALIGNMENT, SYMBOL, START, END, OWNER,
}

/**
 * One labelled value of a record. [link] makes it open another record;
 * [color] and [icon] come from the DM's option lists (statuses, priorities).
 * A [FactKind.FACTION] or [FactKind.OWNER] fact without a link and with an
 * empty [text] means the party, whose default name the app supplies.
 */
data class Fact(
    val kind: FactKind,
    val text: String,
    val link: RecordRef? = null,
    val color: Long? = null,
    val icon: String = "",
)

/** An open or answered question about a character or mystery. */
data class Question(val text: String, val answer: String)

/** A relationship from the `relationships` collection, seen from one character. */
data class Relation(val type: Choice?, val typeId: String, val label: String, val other: RecordRef, val outgoing: Boolean)

/** Groups of records related to the one shown, each named by the app. */
enum class RelatedKind { CHARACTERS, LOCATIONS, PLACES_WITHIN, PEOPLE_HERE, EVENTS, COMPANIONS }

data class RelatedGroup(val kind: RelatedKind, val records: List<RecordRef>)

/** Everything a record's screen shows beyond its article text. */
data class RecordDetails(
    val summary: RecordSummary,
    val facts: List<Fact>,
    val attitudes: List<Choice>,
    val tags: List<String>,
    val known: List<String>,
    val questions: List<Question>,
    val relations: List<Relation>,
    val related: List<RelatedGroup>,
    /** Records whose text links to this one. */
    val mentionedIn: List<RecordRef>,
    /** A host media path for a location's own map, or null. */
    val localMap: String?,
)

fun CampaignDataset.details(page: CampaignPage, key: String): RecordDetails? {
    val record = record(page.collection, key) ?: return null
    val value = record.value
    val settings = settings
    val facts = mutableListOf<Fact>()
    val related = mutableListOf<RelatedGroup>()
    fun fact(kind: FactKind, field: String) {
        value.text(field).takeIf(String::isNotEmpty)?.let { facts += Fact(kind, it) }
    }
    fun option(kind: FactKind, field: String, lookup: (String) -> Choice?) {
        val id = value.text(field).takeIf(String::isNotEmpty) ?: return
        val choice = lookup(id)
        facts += Fact(kind, choice?.label ?: id, color = choice?.color, icon = choice?.icon.orEmpty())
    }
    fun link(kind: FactKind, target: CampaignPage, field: String) {
        ref(target, value.text(field))?.let { facts += Fact(kind, it.name, link = it) }
    }
    fun group(kind: RelatedKind, refs: List<RecordRef>) {
        if (refs.isNotEmpty()) related += RelatedGroup(kind, refs)
    }
    fun records(target: CampaignPage, matches: (JsonObject) -> Boolean): List<RecordRef> =
        records(target.collection).filter { it.key != key && matches(it.value) }.map { it.ref(target) }

    when (page) {
        CampaignPage.CHARACTERS -> {
            option(FactKind.STATUS, "status", settings::characterStatus)
            fact(FactKind.TITLE, "title")
            fact(FactKind.RANK, "rank")
            fact(FactKind.SPECIES, "species")
            option(FactKind.GENDER, "gender", settings::gender)
            fact(FactKind.AGE, "age")
            factionFact(value.text("faction"), settings)?.let(facts::add)
            link(FactKind.LOCATION, CampaignPage.LOCATIONS, "location")
            group(RelatedKind.COMPANIONS, records(CampaignPage.COMPANIONS) {
                it.text("ownerType") == "character" && it.text("ownerId") == key
            })
            group(RelatedKind.EVENTS, records(CampaignPage.EVENTS) { key in it.textList("characters") })
        }
        CampaignPage.LOCATIONS -> {
            fact(FactKind.TYPE, "type")
            fact(FactKind.REGION, "region")
            link(FactKind.PARENT, CampaignPage.LOCATIONS, "parentId")
            option(FactKind.MARKER, "pinType", settings::pinType)
            group(RelatedKind.PLACES_WITHIN, records(CampaignPage.LOCATIONS) { it.text("parentId") == key })
            group(RelatedKind.PEOPLE_HERE, records(CampaignPage.CHARACTERS) { character ->
                character.text("location") == key ||
                    (character["locationRoles"] as? JsonArray).orEmpty().any { (it as? JsonObject)?.text("locationId") == key }
            })
            group(RelatedKind.EVENTS, records(CampaignPage.EVENTS) { key in it.textList("locations") })
        }
        CampaignPage.EVENTS -> {
            fact(FactKind.DATE, "date")
            value.positiveInt("sitting")?.let { facts += Fact(FactKind.SESSION, it.toString()) }
            option(FactKind.PRIORITY, "priority", settings::eventPriority)
            link(FactKind.CONSEQUENCE, CampaignPage.EVENTS, "consequence")
            group(RelatedKind.CHARACTERS, value.textList("characters").mapNotNull { ref(CampaignPage.CHARACTERS, it) })
            group(RelatedKind.LOCATIONS, value.textList("locations").mapNotNull { ref(CampaignPage.LOCATIONS, it) })
        }
        CampaignPage.MYSTERIES -> option(FactKind.PRIORITY, "priority", settings::eventPriority)
        CampaignPage.FACTIONS, CampaignPage.ARTIFACTS -> Unit
        CampaignPage.PANTHEON -> {
            fact(FactKind.TITLE, "title")
            fact(FactKind.DOMAIN, "domain")
            fact(FactKind.ALIGNMENT, "alignment")
            fact(FactKind.SYMBOL, "symbol")
        }
        CampaignPage.HISTORY -> {
            fact(FactKind.START, "start")
            fact(FactKind.END, "end")
        }
        CampaignPage.COMPANIONS -> {
            fact(FactKind.SPECIES, "species")
            when (value.text("ownerType")) {
                "party" -> facts += Fact(FactKind.OWNER, settings.party.name)
                "character" -> link(FactKind.OWNER, CampaignPage.CHARACTERS, "ownerId")
            }
        }
    }

    return RecordDetails(
        summary = record.summary(page),
        facts = facts,
        attitudes = (value["attitudes"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.text("id") }
            .mapNotNull(settings::attitude),
        tags = value.textList("tags"),
        known = value.textList("known"),
        questions = questions(value[if (page == CampaignPage.MYSTERIES) "questions" else "unknown"]),
        relations = if (page == CampaignPage.CHARACTERS) relations(key, settings) else emptyList(),
        related = related,
        mentionedIn = mentionsOf(page, key),
        localMap = value.text("localMap").takeIf(::isMediaPath),
    )
}

/** Visible records whose article text links to the record [key] on [page], in page order. */
fun CampaignDataset.mentionsOf(page: CampaignPage, key: String): List<RecordRef> =
    CampaignPage.entries.flatMap { source ->
        records(source.collection).filter { record ->
            !(source == page && record.key == key) && source.articleFields.any { field ->
                CampaignMarkdown.links(record.value.text(field), this).any { it == page to key }
            }
        }.map { it.ref(source) }
    }

/** A link to the visible record [key] on [page], or null when this session cannot see it. */
fun CampaignDataset.recordRef(page: CampaignPage, key: String): RecordRef? =
    key.takeIf(String::isNotEmpty)?.let { record(page.collection, it) }?.ref(page)

/** A link to the record at a `<page id>/<key>` address, as the recently opened list stores them. */
fun CampaignDataset.recordRef(address: String): RecordRef? {
    val page = CampaignPage.byId(address.substringBefore('/')) ?: return null
    return recordRef(page, address.substringAfter('/', ""))
}

private fun CampaignDataset.ref(page: CampaignPage, key: String): RecordRef? = recordRef(page, key)

private fun CampaignRecord.ref(page: CampaignPage): RecordRef {
    val summary = summary(page)
    return RecordRef(page, key, summary.name, summary.portrait, summary.icon)
}

/** "party" is the players' group; other values name a faction record, and anything else is no faction. */
private fun CampaignDataset.factionFact(id: String, settings: CampaignSettings): Fact? = when {
    id == "party" -> Fact(FactKind.FACTION, settings.party.name, color = settings.party.color, icon = settings.party.badge)
    else -> ref(CampaignPage.FACTIONS, id)?.let { Fact(FactKind.FACTION, it.name, link = it) }
}

private fun CampaignDataset.relations(key: String, settings: CampaignSettings): List<Relation> =
    records("relationships").mapNotNull { record ->
        val value = record.value
        val outgoing = value.text("source") == key
        if (!outgoing && value.text("target") != key) return@mapNotNull null
        val other = ref(CampaignPage.CHARACTERS, value.text(if (outgoing) "target" else "source")) ?: return@mapNotNull null
        val typeId = value.text("type")
        Relation(settings.relationshipType(typeId), typeId, value.text("label"), other, outgoing)
    }

private fun questions(element: kotlinx.serialization.json.JsonElement?): List<Question> =
    (element as? JsonArray).orEmpty().mapNotNull { item ->
        val question = item.stringOrNull() ?: (item as? JsonObject)?.text("text")
        val answer = (item as? JsonObject)?.text("answer").orEmpty()
        question?.trim()?.takeIf(String::isNotEmpty)?.let { Question(it, answer) }
    }
