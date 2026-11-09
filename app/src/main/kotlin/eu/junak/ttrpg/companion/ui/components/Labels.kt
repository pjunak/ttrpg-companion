package eu.junak.ttrpg.companion.ui.components

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.text.RelativeDateTimeFormatter
import android.icu.util.ULocale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.HistoryEdu
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.QuestionMark
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.FactKind
import eu.junak.ttrpg.companion.core.model.RelatedKind
import eu.junak.ttrpg.companion.core.model.Role
import java.util.Date

/** Names and icons for the host's concepts, in the reader's language. */
@Composable
fun pageLabel(page: CampaignPage): String = stringResource(
    when (page) {
        CampaignPage.CHARACTERS -> R.string.page_characters
        CampaignPage.LOCATIONS -> R.string.page_locations
        CampaignPage.EVENTS -> R.string.page_events
        CampaignPage.MYSTERIES -> R.string.page_mysteries
        CampaignPage.FACTIONS -> R.string.page_factions
        CampaignPage.PANTHEON -> R.string.page_pantheon
        CampaignPage.ARTIFACTS -> R.string.page_artifacts
        CampaignPage.HISTORY -> R.string.page_history
        CampaignPage.COMPANIONS -> R.string.page_companions
    },
)

fun pageIcon(page: CampaignPage): ImageVector = when (page) {
    CampaignPage.CHARACTERS -> Icons.Outlined.Person
    CampaignPage.LOCATIONS -> Icons.Outlined.Place
    CampaignPage.EVENTS -> Icons.Outlined.HourglassTop
    CampaignPage.MYSTERIES -> Icons.Outlined.QuestionMark
    CampaignPage.FACTIONS -> Icons.Outlined.Groups
    CampaignPage.PANTHEON -> Icons.Outlined.AutoAwesome
    CampaignPage.ARTIFACTS -> Icons.Outlined.Diamond
    CampaignPage.HISTORY -> Icons.Outlined.HistoryEdu
    CampaignPage.COMPANIONS -> Icons.Outlined.Pets
}

@Composable
fun roleLabel(role: Role?): String = stringResource(
    when (role) {
        Role.DM -> R.string.role_dm
        Role.PLAYER -> R.string.role_player
        null -> R.string.role_reader
    },
)

@Composable
fun factLabel(kind: FactKind): String = stringResource(
    when (kind) {
        FactKind.STATUS -> R.string.fact_status
        FactKind.TITLE -> R.string.fact_title
        FactKind.RANK -> R.string.fact_rank
        FactKind.SPECIES -> R.string.fact_species
        FactKind.GENDER -> R.string.fact_gender
        FactKind.AGE -> R.string.fact_age
        FactKind.FACTION -> R.string.fact_faction
        FactKind.LOCATION -> R.string.fact_location
        FactKind.TYPE -> R.string.fact_type
        FactKind.REGION -> R.string.fact_region
        FactKind.PARENT -> R.string.fact_parent
        FactKind.MARKER -> R.string.fact_marker
        FactKind.DATE -> R.string.fact_date
        FactKind.SESSION -> R.string.fact_session
        FactKind.PRIORITY -> R.string.fact_priority
        FactKind.CONSEQUENCE -> R.string.fact_consequence
        FactKind.DOMAIN -> R.string.fact_domain
        FactKind.ALIGNMENT -> R.string.fact_alignment
        FactKind.SYMBOL -> R.string.fact_symbol
        FactKind.START -> R.string.fact_start
        FactKind.END -> R.string.fact_end
        FactKind.OWNER -> R.string.fact_owner
    },
)

@Composable
fun relatedLabel(kind: RelatedKind): String = stringResource(
    when (kind) {
        RelatedKind.CHARACTERS -> R.string.related_characters
        RelatedKind.LOCATIONS -> R.string.related_locations
        RelatedKind.PLACES_WITHIN -> R.string.related_places_within
        RelatedKind.PEOPLE_HERE -> R.string.related_people_here
        RelatedKind.EVENTS -> R.string.related_events
        RelatedKind.COMPANIONS -> R.string.related_companions
    },
)

/** The heading of an article field after the first; unknown fields keep their name. */
@Composable
fun fieldLabel(field: String): String = when (field) {
    "description" -> stringResource(R.string.field_description)
    "circumstances" -> stringResource(R.string.field_circumstances)
    "history" -> stringResource(R.string.field_history)
    "mapNotes" -> stringResource(R.string.field_map_notes)
    "short" -> stringResource(R.string.field_short)
    "summary" -> stringResource(R.string.field_summary)
    "body" -> stringResource(R.string.field_body)
    "note" -> stringResource(R.string.field_note)
    else -> field
}

/** The party's name from settings, or the app's default. */
@Composable
fun partyName(name: String): String = name.ifEmpty { stringResource(R.string.party_default_name) }

/**
 * "just now", "5 min. ago", "3 days ago" or a date, in the language the
 * screen is shown in (which may be the site's rather than the device's).
 */
@Composable
fun relativeTime(at: Long, now: Long = System.currentTimeMillis()): String {
    val locale = LocalConfiguration.current.locales[0]
    val elapsed = now - at
    if (elapsed in 0 until MINUTE) return stringResource(R.string.time_just_now)
    val formatter = RelativeDateTimeFormatter.getInstance(
        ULocale.forLocale(locale),
        null,
        RelativeDateTimeFormatter.Style.SHORT,
        DisplayContext.CAPITALIZATION_NONE,
    )
    fun ago(amount: Long, unit: RelativeDateTimeFormatter.RelativeUnit) =
        formatter.format(amount.toDouble(), RelativeDateTimeFormatter.Direction.LAST, unit)
    return when {
        elapsed < 0 -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(at))
        elapsed < HOUR -> ago(elapsed / MINUTE, RelativeDateTimeFormatter.RelativeUnit.MINUTES)
        elapsed < DAY -> ago(elapsed / HOUR, RelativeDateTimeFormatter.RelativeUnit.HOURS)
        elapsed < WEEK -> ago(elapsed / DAY, RelativeDateTimeFormatter.RelativeUnit.DAYS)
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(at))
    }
}

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE
private const val DAY = 24 * HOUR
private const val WEEK = 7 * DAY
