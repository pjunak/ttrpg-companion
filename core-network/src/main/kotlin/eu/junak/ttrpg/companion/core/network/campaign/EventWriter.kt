package eu.junak.ttrpg.companion.core.network.campaign

import eu.junak.ttrpg.companion.core.model.CampaignMutation
import eu.junak.ttrpg.companion.core.model.EventBase
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.model.EventSave
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.prepareEventDelete
import eu.junak.ttrpg.companion.core.model.prepareEventSave
import eu.junak.ttrpg.companion.core.network.site.Site
import javax.inject.Inject
import javax.inject.Singleton

/** What happened to a session note or event edit. */
sealed interface EventSaveResult {
    data object Saved : EventSaveResult

    /** Someone changed these stored fields while the user edited; saving again with overwrite replaces them. */
    data class Conflict(val fields: List<String>) : EventSaveResult

    data object Gone : EventSaveResult

    data class NotSaved(val reason: WriteResult) : EventSaveResult
}

/**
 * Saves events online against the latest campaign. A write that races with
 * another change is prepared again on the refreshed campaign, so edits to
 * different fields merge; only a clash on the same field asks the user.
 */
@Singleton
class EventWriter @Inject constructor(private val campaigns: CampaignRepository) {

    suspend fun save(site: Site, base: EventBase, fields: EventFields, overwrite: Boolean = false): EventSaveResult {
        repeat(MAX_ATTEMPTS) {
            val dataset = campaigns.state(site).value.dataset ?: return EventSaveResult.NotSaved(WriteResult.Failed(""))
            val mutation = when (val prepared = dataset.prepareEventSave(base, fields, site.role == Role.DM, overwrite)) {
                is EventSave.Ready -> prepared.mutation
                is EventSave.Conflict -> return EventSaveResult.Conflict(prepared.fields)
                EventSave.Gone -> return EventSaveResult.Gone
                EventSave.AlreadySaved -> return EventSaveResult.Saved
            }
            when (val result = campaigns.commit(site, listOf(mutation))) {
                WriteResult.Saved -> return EventSaveResult.Saved
                WriteResult.Conflict -> Unit // The campaign was refreshed; prepare again.
                else -> return EventSaveResult.NotSaved(result)
            }
        }
        return EventSaveResult.NotSaved(WriteResult.Conflict)
    }

    suspend fun delete(site: Site, key: String): EventSaveResult {
        val dataset = campaigns.state(site).value.dataset ?: return EventSaveResult.NotSaved(WriteResult.Failed(""))
        val mutation: CampaignMutation = dataset.prepareEventDelete(key) ?: return EventSaveResult.Saved
        return when (val result = campaigns.commit(site, listOf(mutation))) {
            WriteResult.Saved -> EventSaveResult.Saved
            else -> EventSaveResult.NotSaved(result)
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
    }
}
