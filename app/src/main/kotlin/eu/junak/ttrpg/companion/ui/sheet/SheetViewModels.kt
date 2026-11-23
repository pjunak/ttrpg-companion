package eu.junak.ttrpg.companion.ui.sheet

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.core.network.campaign.CampaignRepository
import eu.junak.ttrpg.companion.core.network.campaign.CampaignState
import eu.junak.ttrpg.companion.core.network.character.CharacterNotice
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.CharacterSessions
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.events.EventStream
import eu.junak.ttrpg.companion.core.network.services.CharacterService
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.navigation.RecordRoute
import eu.junak.ttrpg.companion.navigation.SheetRoute
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * One open character sheet: its session, which lives as long as this
 * ViewModel, follows the site's event stream and is closed with it.
 */
@HiltViewModel
class SheetViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val sites: SiteStore,
    campaigns: CampaignRepository,
    sessions: CharacterSessions,
    events: EventStream,
) : ViewModel() {
    private val route = savedState.toRoute<SheetRoute>()
    val key: String = route.key
    val site: Site? = sites.site(route.siteId)
    private val session = site?.let { sessions.open(it, route.key, viewModelScope, events.events(it.baseUrl)) }

    /** Without the site (it was removed) the screen leaves; this state is never shown. */
    val state: StateFlow<CharacterSessionState> = session?.state ?: MutableStateFlow(CharacterSessionState(load = SheetLoad.Loading)).asStateFlow()

    val notices: Flow<CharacterNotice> = session?.notices ?: emptyFlow()

    /** The character's name from its campaign record. */
    val name: StateFlow<String?> = (site?.let(campaigns::state) ?: MutableStateFlow(CampaignState()))
        .map { campaign -> campaign.dataset?.record(CampaignPage.CHARACTERS.collection, route.key)?.value?.text("name")?.ifEmpty { null } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Whether this character is the site's "My character". */
    val myCharacter: StateFlow<Boolean> = sites.myCharacter(route.siteId)
        .map { it == route.key }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Makes this the site's "My character" (replacing another) or, switched off, makes none. */
    fun setMyCharacter(on: Boolean) {
        viewModelScope.launch {
            sites.setMyCharacter(route.siteId, myCharacterAfter(sites.myCharacter(route.siteId).first(), route.key, on))
        }
    }

    fun play(change: PlayChange) {
        session?.play(change)
    }

    fun edit(edit: (JsonObject) -> JsonObject) {
        session?.edit(edit)
    }

    /** Switches inspiration on the newest saved inputs, so taps in a row alternate. */
    fun toggleInspiration() = edit { inputs ->
        val play = inputs["play"] as? JsonObject
        val inspired = (play?.get("inspiration") as? JsonPrimitive)?.booleanOrNull ?: false
        CharacterInputs.setInspiration(inputs, !inspired)
    }

    fun setCondition(id: String, level: Int) = edit { CharacterInputs.setCondition(it, id, level) }

    fun removeCondition(id: String) = edit { CharacterInputs.removeCondition(it, id) }

    fun endConcentration() = play(PlayChange.EndConcentration)

    fun retry() {
        session?.retry()
    }

    fun discard() {
        session?.discard()
    }

    fun reload() {
        session?.reload()
    }

    private val returns = ReturnWatch()

    fun screenStopped() = returns.stopped()

    /** Back from another screen or app (signing in again, say), the sheet loads again; not on the first open. */
    fun screenStarted() {
        if (returns.started()) reload()
    }

    override fun onCleared() {
        session?.close()
    }
}

/**
 * Whether a character record page offers "Open sheet" ([offersSheet]). The
 * page never waits for it: the action appears once the host answers, and an
 * error only leaves it away.
 */
@HiltViewModel
class SheetEntryViewModel @Inject constructor(
    savedState: SavedStateHandle,
    sites: SiteStore,
    characters: CharacterService,
) : ViewModel() {
    private val _offered = MutableStateFlow(false)
    val offered: StateFlow<Boolean> = _offered.asStateFlow()

    init {
        val route = savedState.toRoute<RecordRoute>()
        val site = sites.site(route.siteId)
        if (route.pageId == CampaignPage.CHARACTERS.id && site?.role != null) {
            viewModelScope.launch {
                val availability = characters.availability(site)
                val hasCopy = withContext(Dispatchers.IO) { sites.hasCharacter(site.id, route.key) }
                _offered.value = offersSheet(signedIn = true, availability, hasCopy)
            }
        }
    }
}
