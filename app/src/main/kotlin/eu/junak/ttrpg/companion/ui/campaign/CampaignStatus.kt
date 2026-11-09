package eu.junak.ttrpg.companion.ui.campaign

import android.text.format.DateUtils
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.ui.components.Notice

/** Says when the shown campaign is a cached copy or the public view after a lost session. */
@Composable
fun CampaignStatus(state: CampaignState, onSignIn: () -> Unit) {
    when (val problem = state.problem) {
        is CampaignProblem.Unavailable -> if (state.dataset != null) {
            val time = state.fetchedAt?.let {
                DateUtils.getRelativeTimeSpanString(it, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
            }.orEmpty()
            Notice(stringResource(R.string.status_cached, time, problem.detail))
        }
        CampaignProblem.SessionExpired -> Notice(stringResource(R.string.status_expired)) {
            TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in_again)) }
        }
        null -> Unit
    }
}
