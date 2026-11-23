package eu.junak.ttrpg.companion.ui.sessions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R

/**
 * Asks before leaving a note or event screen: whether to discard unsaved
 * text, or whether to leave while a save is on its way. The ViewModel closes
 * the saving question itself once the save answers.
 */
@Composable
fun LeaveDialog(leave: Leave, discardTitle: String, discardText: String, onLeave: () -> Unit, onStay: () -> Unit) {
    when (leave) {
        Leave.CONFIRM_DISCARD -> AlertDialog(
            onDismissRequest = onStay,
            title = { Text(discardTitle) },
            text = { Text(discardText) },
            confirmButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.action_keep_editing)) } },
        )
        Leave.SAVING -> AlertDialog(
            onDismissRequest = onStay,
            title = { Text(stringResource(R.string.leave_saving_title)) },
            text = { Text(stringResource(R.string.leave_saving_text)) },
            confirmButton = { TextButton(onClick = onStay) { Text(stringResource(R.string.action_wait)) } },
            dismissButton = { TextButton(onClick = onLeave) { Text(stringResource(R.string.action_leave)) } },
        )
        Leave.STAY, Leave.GO -> Unit
    }
}
