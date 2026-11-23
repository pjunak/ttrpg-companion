package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Notice
import eu.junak.ttrpg.companion.ui.components.relativeTime

/** Says when the shown campaign is a cached copy, the public view after a lost session or from an unsupported host. */
@Composable
fun CampaignStatus(state: CampaignState, onSignIn: () -> Unit) {
    when (val problem = state.problem) {
        is CampaignProblem.Unavailable -> if (state.dataset != null) {
            val time = state.fetchedAt?.let { relativeTime(it) }.orEmpty()
            Notice(stringResource(R.string.status_cached, time, hostFailureText(problem.failure)))
        }
        CampaignProblem.SessionExpired -> Notice(stringResource(R.string.status_expired)) {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in_again)) }
        }
        is CampaignProblem.UnsupportedHost -> Notice(stringResource(R.string.problem_unsupported, problem.version))
        null -> Unit
    }
}

/** A failed host call in the reader's language; the technical detail shows only when the cause is unknown. */
@Composable
fun hostFailureText(failure: HostFailure): String = when (failure.reason) {
    HostFailure.Reason.OFFLINE -> stringResource(R.string.failure_offline)
    HostFailure.Reason.TIMEOUT -> stringResource(R.string.failure_timeout)
    HostFailure.Reason.HOST_ERROR -> stringResource(R.string.failure_host_error, failure.status ?: 0)
    HostFailure.Reason.MALFORMED -> stringResource(R.string.failure_malformed)
    HostFailure.Reason.OTHER -> stringResource(R.string.failure_other, failure.detail)
}

/** Why the campaign could not be loaded, as one sentence or two. */
@Composable
fun campaignProblemText(problem: CampaignProblem): String = when (problem) {
    is CampaignProblem.Unavailable -> stringResource(R.string.campaign_failed, hostFailureText(problem.failure))
    is CampaignProblem.UnsupportedHost -> stringResource(R.string.problem_unsupported, problem.version)
    CampaignProblem.SessionExpired -> stringResource(R.string.status_expired)
}

/**
 * A site screen's whole content while it has no campaign to show: a spinner
 * while it loads, otherwise why it could not be loaded and a way to try again.
 */
@Composable
fun CampaignMissing(state: CampaignState, onRetry: () -> Unit) {
    val problem = state.problem
    if (problem == null || state.refreshing) {
        CenteredMessage(stringResource(R.string.campaign_loading), loading = true)
    } else {
        CenteredMessage(campaignProblemText(problem)) {
            TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
        }
    }
}
