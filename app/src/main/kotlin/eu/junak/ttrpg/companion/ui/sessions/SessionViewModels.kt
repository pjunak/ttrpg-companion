package eu.junak.ttrpg.companion.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.EventBase
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.model.EventProblem
import eu.junak.ttrpg.companion.core.model.HostJson
import eu.junak.ttrpg.companion.core.model.Role
import eu.junak.ttrpg.companion.core.model.newRecordKey
import eu.junak.ttrpg.companion.core.network.campaign.CampaignProblem
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.core.network.campaign.EventSaveResult
import eu.junak.ttrpg.companion.core.network.campaign.EventWriter
import eu.junak.ttrpg.companion.core.network.campaign.WriteResult
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.navigation.EventEditRoute
import eu.junak.ttrpg.companion.navigation.SessionRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject

/** Why a note or event was not saved, for the screen to explain. */
sealed interface SaveProblem {
    data class Invalid(val problem: EventProblem) : SaveProblem
    data class Conflict(val fields: List<String>) : SaveProblem
    data object Gone : SaveProblem
    data object ChangedBeforeDelete : SaveProblem
    data class NotSaved(val reason: WriteResult) : SaveProblem
}

/** Where a request to leave a note or event screen stands. */
enum class Leave {
    /** Nothing asked: the screen stays. */
    STAY,

    /** Unsaved text would be lost; ask before discarding it. */
    CONFIRM_DISCARD,

    /** A save is on its way; leaving now would hide whether it worked. Settles itself when the save answers. */
    SAVING,

    /** Nothing would be lost: leave now. */
    GO,
}

/**
 * The quick note composer of one session. The text survives rotation and
 * process death; a note keeps one record key while its text is unchanged, so
 * retrying after a lost answer never saves it twice.
 */
@HiltViewModel
class SessionNotesViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val sites: SiteStore,
    private val writer: EventWriter,
) : ViewModel() {

    private val route = savedState.toRoute<SessionRoute>()

    private var typed by mutableStateOf(savedState[KEY_TEXT] ?: "")

    /** The note being written, as Compose state so the text field never lags behind typing. */
    val text: String get() = typed
    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending.asStateFlow()
    private val _problem = MutableStateFlow<SaveProblem?>(null)
    val problem: StateFlow<SaveProblem?> = _problem.asStateFlow()
    private val _leave = MutableStateFlow(Leave.STAY)
    val leave: StateFlow<Leave> = _leave.asStateFlow()

    fun setText(value: String) {
        keep(value)
        _problem.value = null
    }

    fun send() {
        val site = sites.site(route.siteId) ?: return
        val note = text
        val fields = EventFields.note(note, route.number)
        fields.problem?.let { _problem.value = SaveProblem.Invalid(it); return }
        val key = savedState.get<String>(KEY_PENDING)?.takeIf { savedState.get<String>(KEY_PENDING_TEXT) == note }
            ?: newRecordKey(fields.name, UUID.randomUUID().toString())
        savedState[KEY_PENDING] = key
        savedState[KEY_PENDING_TEXT] = note
        _problem.value = null
        viewModelScope.launch {
            _sending.value = true
            val result = writer.save(site, EventBase(key, JsonObject(emptyMap()), 0), fields)
            _sending.value = false
            if (result == EventSaveResult.Saved) {
                if (text == note) keep("")
                savedState.remove<String>(KEY_PENDING)
                savedState.remove<String>(KEY_PENDING_TEXT)
            } else {
                _problem.value = result.problem()
            }
            if (_leave.value == Leave.SAVING) {
                // The user wanted to leave: go once the note is saved, stay to show a problem.
                _leave.value = when {
                    result != EventSaveResult.Saved -> Leave.STAY
                    text.isBlank() -> Leave.GO
                    else -> Leave.CONFIRM_DISCARD
                }
            }
        }
    }

    /**
     * Hands the note to the full editor: returns its text and empties the
     * composer, or null while it is being sent.
     */
    fun handOver(): String? {
        if (_sending.value) return null
        val handed = text
        setText("")
        return handed
    }

    /** Leaving the session: at once, unless a note would be lost or is still being sent. */
    fun requestLeave() {
        _leave.value = when {
            _sending.value -> Leave.SAVING
            text.isNotBlank() -> Leave.CONFIRM_DISCARD
            else -> Leave.GO
        }
    }

    /** Leaves although a note would be lost or its save not seen. */
    fun leaveAnyway() {
        _leave.value = Leave.GO
    }

    fun stay() {
        _leave.value = Leave.STAY
    }

    private fun keep(value: String) {
        typed = value
        savedState[KEY_TEXT] = value
    }

    private companion object {
        const val KEY_TEXT = "note"
        const val KEY_PENDING = "pendingKey"
        const val KEY_PENDING_TEXT = "pendingText"
    }
}

/** Where the event editor is. */
sealed interface EditorStatus {
    data object Loading : EditorStatus
    data object Editing : EditorStatus
    data object Saving : EditorStatus

    /** The event was saved; the editor closes. */
    data object Saved : EditorStatus

    /** The event was deleted; the editor closes and so does the deleted event's page. */
    data object Deleted : EditorStatus
    data class Problem(val problem: SaveProblem) : EditorStatus

    /** The campaign could not be loaded, so there is no event to edit yet; [problem] says why when known. */
    data class CannotLoad(val problem: CampaignProblem?) : EditorStatus
}

/**
 * Creates or edits one event. The editor remembers the event as it was when
 * editing began, so a save merges with changes made elsewhere meanwhile; its
 * draft survives rotation and process death.
 */
@HiltViewModel
class EventEditorViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val sites: SiteStore,
    private val campaigns: CampaignRepository,
    private val writer: EventWriter,
) : ViewModel() {

    private val route = savedState.toRoute<EventEditRoute>()
    val site = sites.site(route.siteId)
    val campaign: StateFlow<CampaignState> = site?.let(campaigns::state) ?: MutableStateFlow(CampaignState())
    val creating: Boolean = route.key == null

    /** Whether the DM-only switch applies: the role the campaign was fetched for, which saving uses too. */
    val canManageVisibility: Boolean get() = campaign.value.role == Role.DM

    private var base: EventBase? = restoreBase()
    private var initial: EventFields? = null

    /** The draft, as Compose state so text fields never lag behind typing; null until the event opened. */
    var fields: EventFields? by mutableStateOf(null)
        private set
    private val _status = MutableStateFlow<EditorStatus>(EditorStatus.Loading)
    val status: StateFlow<EditorStatus> = _status.asStateFlow()
    private val _leave = MutableStateFlow(Leave.STAY)
    val leave: StateFlow<Leave> = _leave.asStateFlow()

    init {
        viewModelScope.launch { open() }
    }

    /** Tries again to load the campaign after it could not be loaded. */
    fun retry() {
        if (_status.value !is EditorStatus.CannotLoad) return
        _status.value = EditorStatus.Loading
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        if (campaign.value.dataset == null && site != null) campaigns.open(site)
        val dataset = campaign.value.dataset
        if (dataset == null) {
            _status.value = EditorStatus.CannotLoad(campaign.value.problem)
            return
        }
        val opening = base ?: if (route.key == null) {
            EventBase("", JsonObject(emptyMap()), 0)
        } else {
            dataset.record(CampaignPage.EVENTS.collection, route.key)?.let(EventBase::of)
        }
        if (opening == null) {
            _status.value = EditorStatus.Problem(SaveProblem.Gone)
            return
        }
        remember(opening)
        initial = if (opening.creating) {
            if (route.note.isBlank()) EventFields(sitting = route.sitting) else EventFields.note(route.note, route.sitting)
        } else {
            EventFields.of(opening.value)
        }
        fields = restoreFields() ?: initial
        _status.value = EditorStatus.Editing
    }

    /**
     * Whether leaving would lose something: an edited event that differs from
     * how it opened, or a new event with any content (including a note moved
     * here from the composer, which no longer holds it).
     */
    val dirty: Boolean
        get() {
            val fields = fields ?: return false
            return if (creating) fields != EventFields(sitting = route.sitting) else fields != initial
        }

    /** Changes the draft; ignored while saving, when the form is locked. */
    fun update(fields: EventFields) {
        if (_status.value == EditorStatus.Saving) return
        this.fields = fields
        saveDraft(fields)
        if (_status.value is EditorStatus.Problem) _status.value = EditorStatus.Editing
    }

    fun save(overwrite: Boolean = false) {
        val site = site ?: return
        val fields = fields ?: return
        fields.problem?.let { _status.value = EditorStatus.Problem(SaveProblem.Invalid(it)); return }
        var opening = base ?: return
        if (opening.creating && opening.key.isEmpty()) {
            opening = opening.copy(key = newRecordKey(fields.name, UUID.randomUUID().toString()))
            remember(opening)
        }
        viewModelScope.launch {
            _status.value = EditorStatus.Saving
            val result = writer.save(site, opening, fields, overwrite)
            _status.value = if (result == EventSaveResult.Saved) EditorStatus.Saved else EditorStatus.Problem(result.problem())
            settleLeave(result)
        }
    }

    fun delete() {
        val site = site ?: return
        val opening = base?.takeUnless { it.creating } ?: return
        viewModelScope.launch {
            _status.value = EditorStatus.Saving
            val result = writer.delete(site, opening)
            _status.value = if (result == EventSaveResult.Saved) EditorStatus.Deleted else EditorStatus.Problem(result.problem())
            settleLeave(result)
        }
    }

    fun dismissProblem() {
        if (_status.value is EditorStatus.Problem) _status.value = EditorStatus.Editing
    }

    /**
     * After a delete refused because the event changed: starts again from the
     * event as it is stored now, replacing the draft, so the user sees the
     * change and can delete it or keep editing.
     */
    fun reload() {
        val key = base?.takeUnless { it.creating }?.key ?: return
        val record = campaign.value.dataset?.record(CampaignPage.EVENTS.collection, key)
        if (record == null) {
            _status.value = EditorStatus.Problem(SaveProblem.Gone)
            return
        }
        val opening = EventBase.of(record)
        remember(opening)
        val latest = EventFields.of(opening.value)
        initial = latest
        fields = latest
        saveDraft(latest)
        _status.value = EditorStatus.Editing
    }

    /** Leaving the editor: at once, unless changes would be lost or a save is still on its way. */
    fun requestLeave() {
        _leave.value = when {
            _status.value == EditorStatus.Saving -> Leave.SAVING
            dirty -> Leave.CONFIRM_DISCARD
            else -> Leave.GO
        }
    }

    /** Leaves although changes would be lost or a save not seen. */
    fun leaveAnyway() {
        _leave.value = Leave.GO
    }

    fun stay() {
        _leave.value = Leave.STAY
    }

    /** The user wanted to leave while saving: go once it is done, stay to show a problem. */
    private fun settleLeave(result: EventSaveResult) {
        if (_leave.value == Leave.SAVING) _leave.value = if (result == EventSaveResult.Saved) Leave.GO else Leave.STAY
    }

    private fun remember(opening: EventBase) {
        base = opening
        savedState[KEY_BASE_KEY] = opening.key
        savedState[KEY_BASE_REVISION] = opening.revision
        savedState[KEY_BASE_VALUE] = opening.value.toString()
    }

    private fun restoreBase(): EventBase? {
        val key = savedState.get<String>(KEY_BASE_KEY) ?: return null
        val revision = savedState.get<Long>(KEY_BASE_REVISION) ?: return null
        val value = savedState.get<String>(KEY_BASE_VALUE)?.let { HostJson.parseToJsonElement(it) as? JsonObject } ?: return null
        return EventBase(key, value, revision)
    }

    private fun saveDraft(fields: EventFields) {
        savedState[KEY_DRAFT] = arrayListOf(
            fields.name, fields.short, fields.description, fields.date, fields.sitting.toString(), fields.priority,
            fields.characters.joinToString(LIST_SEPARATOR), fields.locations.joinToString(LIST_SEPARATOR), fields.dmOnly.toString(),
        )
    }

    private fun restoreFields(): EventFields? {
        val draft = savedState.get<ArrayList<String>>(KEY_DRAFT)?.takeIf { it.size == DRAFT_SIZE } ?: return null
        fun list(text: String) = text.split(LIST_SEPARATOR).filter(String::isNotEmpty)
        return EventFields(
            name = draft[0], short = draft[1], description = draft[2], date = draft[3],
            sitting = draft[4].toIntOrNull() ?: 1, priority = draft[5],
            characters = list(draft[6]), locations = list(draft[7]), dmOnly = draft[8].toBoolean(),
        )
    }

    private companion object {
        const val KEY_BASE_KEY = "baseKey"
        const val KEY_BASE_REVISION = "baseRevision"
        const val KEY_BASE_VALUE = "baseValue"
        const val KEY_DRAFT = "draft"
        const val DRAFT_SIZE = 9
        // Record keys never contain control characters, so this cannot appear inside one.
        const val LIST_SEPARATOR = "\u001F"
    }
}

private fun EventSaveResult.problem(): SaveProblem = when (this) {
    is EventSaveResult.Conflict -> SaveProblem.Conflict(fields)
    EventSaveResult.Gone -> SaveProblem.Gone
    EventSaveResult.Changed -> SaveProblem.ChangedBeforeDelete
    is EventSaveResult.NotSaved -> SaveProblem.NotSaved(reason)
    EventSaveResult.Saved -> error("a saved event has no problem")
}
