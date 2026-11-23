package eu.junak.ttrpg.companion.core.network.campaign

import eu.junak.ttrpg.companion.core.model.EventBase
import eu.junak.ttrpg.companion.core.model.EventDelete
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.model.EventSave
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.prepareEventDelete
import eu.junak.ttrpg.companion.core.model.prepareEventSave
import eu.junak.ttrpg.companion.core.network.di.ApplicationScope
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to a session note or event edit. */
sealed interface EventSaveResult {
    data object Saved : EventSaveResult

    /** Someone changed these stored fields while the user edited; saving again with overwrite replaces them. */
    data class Conflict(val fields: List<String>) : EventSaveResult

    data object Gone : EventSaveResult

    /** Someone changed the event since the editor opened, so it was not deleted. */
    data object Changed : EventSaveResult

    data class NotSaved(val reason: WriteResult) : EventSaveResult
}

/**
 * Saves events online against the latest campaign. A write that races with
 * another change is prepared again on the refreshed campaign, so edits to
 * different fields merge; only a clash on the same field asks the user. A
 * change is prepared for the role its campaign was fetched for, and again
 * when the session's role turns out to differ.
 *
 * Writes run in the application's scope: a caller that is cancelled (a
 * screen being left) stops waiting, but a request on its way still completes
 * and the campaign is still refreshed.
 */
@Singleton
class EventWriter @Inject constructor(
    private val campaigns: CampaignRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    suspend fun save(site: Site, base: EventBase, fields: EventFields, overwrite: Boolean = false): EventSaveResult =
        scope.async { saveNow(site, base, fields, overwrite) }.await()

    /** Deletes the event the editor opened as [base], unless it changed since. */
    suspend fun delete(site: Site, base: EventBase): EventSaveResult = scope.async { deleteNow(site, base) }.await()

    private suspend fun saveNow(site: Site, base: EventBase, fields: EventFields, overwrite: Boolean): EventSaveResult {
        repeat(MAX_ATTEMPTS) {
            val state = campaigns.state(site).value
            val dataset = state.dataset ?: return EventSaveResult.NotSaved(WriteResult.Failed(""))
            // Prepared for the role the campaign was fetched for; the commit checks it against the session.
            val mutation = when (val prepared = dataset.prepareEventSave(base, fields, state.role == Role.DM, overwrite)) {
                is EventSave.Ready -> prepared.mutation
                is EventSave.Conflict -> return EventSaveResult.Conflict(prepared.fields)
                EventSave.Gone -> return EventSaveResult.Gone
                EventSave.AlreadySaved -> return EventSaveResult.Saved
            }
            when (val result = campaigns.commit(site, listOf(mutation), state.role)) {
                WriteResult.Saved -> return EventSaveResult.Saved
                WriteResult.Conflict -> Unit // The campaign was refreshed (new records or role); prepare again.
                else -> return EventSaveResult.NotSaved(result)
            }
        }
        return EventSaveResult.NotSaved(WriteResult.Conflict)
    }

    private suspend fun deleteNow(site: Site, base: EventBase): EventSaveResult {
        repeat(MAX_ATTEMPTS) {
            val state = campaigns.state(site).value
            val dataset = state.dataset ?: return EventSaveResult.NotSaved(WriteResult.Failed(""))
            val mutation = when (val prepared = dataset.prepareEventDelete(base)) {
                is EventDelete.Ready -> prepared.mutation
                EventDelete.Changed -> return EventSaveResult.Changed
                EventDelete.AlreadyGone -> return EventSaveResult.Saved
            }
            when (val result = campaigns.commit(site, listOf(mutation), state.role)) {
                WriteResult.Saved -> return EventSaveResult.Saved
                WriteResult.Conflict -> Unit // The campaign was refreshed (new records or role); prepare again.
                else -> return EventSaveResult.NotSaved(result)
            }
        }
        return EventSaveResult.NotSaved(WriteResult.Conflict)
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
