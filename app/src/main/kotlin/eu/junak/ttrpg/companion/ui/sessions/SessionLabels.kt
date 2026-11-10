package eu.junak.ttrpg.companion.ui.sessions

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.EventProblem
import eu.junak.ttrpg.companion.core.network.campaign.WriteResult

/** Why a note or event edit was not saved, in the reader's language. */
@Composable
fun saveProblemText(problem: SaveProblem): String = when (problem) {
    is SaveProblem.Invalid -> stringResource(
        when (problem.problem) {
            EventProblem.NAME_MISSING -> R.string.problem_name_missing
            EventProblem.NAME_TOO_LONG -> R.string.problem_name_too_long
            EventProblem.SHORT_TOO_LONG -> R.string.problem_short_too_long
            EventProblem.DESCRIPTION_TOO_LONG -> R.string.problem_description_too_long
            EventProblem.SESSION_INVALID -> R.string.problem_session_invalid
        },
    )
    is SaveProblem.Conflict -> stringResource(R.string.save_conflict_title)
    SaveProblem.Gone -> stringResource(R.string.save_gone)
    is SaveProblem.NotSaved -> when (val reason = problem.reason) {
        WriteResult.SignedOut -> stringResource(R.string.save_signed_out)
        WriteResult.Forbidden -> stringResource(R.string.save_forbidden)
        WriteResult.Conflict -> stringResource(R.string.save_busy)
        is WriteResult.Failed -> stringResource(R.string.save_failed, reason.detail.ifEmpty { stringResource(R.string.save_offline) })
        WriteResult.Saved -> ""
    }
}

/** The editor's name for a stored event field, for conflict messages. */
@Composable
fun storedFieldLabel(field: String): String = when (field) {
    "name" -> stringResource(R.string.field_name)
    "short" -> stringResource(R.string.field_short_summary)
    "description" -> stringResource(R.string.field_description)
    "date" -> stringResource(R.string.field_date)
    "priority" -> stringResource(R.string.fact_priority)
    "sitting" -> stringResource(R.string.field_session)
    "characters" -> stringResource(R.string.page_characters)
    "locations" -> stringResource(R.string.page_locations)
    "visibility" -> stringResource(R.string.event_dm_only)
    else -> field
}
