package eu.junak.ttrpg.companion.core.network.character

import eu.junak.ttrpg.companion.core.model.HostEvent
import eu.junak.ttrpg.companion.core.model.HostFormatException
import eu.junak.ttrpg.companion.core.model.character.CharacterAnswer
import eu.junak.ttrpg.companion.core.model.character.CharacterEvaluation
import eu.junak.ttrpg.companion.core.model.character.CharacterRequest
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.CharacterStatus
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.services.AddonServices
import eu.junak.ttrpg.companion.core.network.services.CharacterService
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import eu.junak.ttrpg.companion.core.network.services.ServiceResult
import eu.junak.ttrpg.companion.core.network.site.Site
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** How loading the character went. */
sealed interface SheetLoad {
    data object Loading : SheetLoad

    /**
     * The character has a sheet: [sheet] is what the screens show, read from
     * [CharacterSessionState.answer] (and the last evaluation when the answer
     * has none). A sheet that cannot be read is [Failed] instead.
     */
    data class Sheet(val sheet: CharacterSheet) : SheetLoad

    /** The character exists but has no sheet yet. */
    data object NoSheet : SheetLoad

    /** The site does not offer character sheets, or its host predates them ([hostTooOld]). */
    data class NotOffered(val hostTooOld: Boolean) : SheetLoad

    data object SignedOut : SheetLoad

    /** This session may not read the character, such as a player opening a DM-only one. */
    data object Forbidden : SheetLoad

    /** Loading failed for [failure]; [CharacterSession.reload] tries again. */
    data class Failed(val failure: ServiceFailure) : SheetLoad
}

/** Whether the changes made in this session are saved. */
sealed interface SaveState {
    data object Saved : SaveState

    data object Saving : SaveState

    /**
     * A change's outcome is unknown ([failure]); it is kept exactly as sent and
     * every later change waits behind it. It is sent again automatically after
     * a growing pause, at once on [CharacterSession.retry], and [retrying]
     * while that happens.
     */
    data class NotSaved(val failure: ServiceFailure, val retrying: Boolean) : SaveState
}

/** Something to tell the player once. */
sealed interface CharacterNotice {
    /** The rules refused the change; nothing was saved. [message] is the service's English reason. */
    data class Refused(val message: String) : CharacterNotice

    /** The sheet changed in another session, so the change was not made; the sheet now shows the saved one. */
    data object ChangedElsewhere : CharacterNotice

    /**
     * A change sent again after a lost answer met a sheet changed elsewhere.
     * It may or may not have been saved; the player should check the sheet.
     */
    data object CouldNotConfirm : CharacterNotice

    /** The site's rules changed; changes wait until they are adopted on the website. */
    data object RulesChanged : CharacterNotice

    /** No compatible rules answer; the sheet stays readable but cannot change. */
    data object RulesUnavailable : CharacterNotice

    data class TooLarge(val message: String) : CharacterNotice

    /** This session may not make the change. */
    data object Forbidden : CharacterNotice

    /** The service refused the change for [failure]; nothing was saved. */
    data class NotSaved(val failure: ServiceFailure) : CharacterNotice
}

/**
 * What a character screen shows. [answer] is the last answer that carried the
 * character (with its saved state, or the blank one of a character without a
 * sheet); [evaluation] is the last evaluation seen, kept while answers without
 * one (a refused change, a repeat) arrive. [pending] counts the changes not
 * yet answered: one kept after a lost answer and those waiting behind it,
 * which [CharacterSession.discard] gives up together. [reloading] is true
 * while the sheet loads after a discard; no change is taken meanwhile.
 */
data class CharacterSessionState(
    val load: SheetLoad = SheetLoad.Loading,
    val answer: CharacterAnswer? = null,
    val evaluation: CharacterEvaluation? = null,
    val save: SaveState = SaveState.Saved,
    val pending: Int = 0,
    val signedOut: Boolean = false,
    val reloading: Boolean = false,
) {
    /** Whether changes can be sent: a saved sheet that the current rules evaluate as ready. */
    val canWrite: Boolean
        get() = answer?.state != null && evaluation?.ready == true && !answer.rulesChanged &&
            answer.status != CharacterStatus.UNAVAILABLE && !signedOut && !reloading && load is SheetLoad.Sheet
}

/** The pauses of a session; delays run on its coroutines' clock. */
data class CharacterSessionTiming(
    /** Pauses before each automatic resend of a change whose answer was lost; the last one repeats. */
    val retryDelaysMs: List<Long> = listOf(2_000, 5_000, 15_000, 30_000, 60_000),
    /** How long to wait after a live event before loading, so a burst of events causes one load. */
    val eventPauseMs: Long = 1_000,
) {
    init {
        require(retryDelaysMs.isNotEmpty() && retryDelaysMs.all { it > 0 } && eventPauseMs >= 0) { "pauses are invalid" }
    }
}

/** Opens [CharacterSession]s. */
@Singleton
class CharacterSessions @Inject constructor(private val services: AddonServices) {
    /**
     * Opens character [key] of [site]; the session works in [scope] until it is
     * closed or [scope] ends. [events] is the site's event stream, used to load
     * again after changes made elsewhere.
     */
    fun open(
        site: Site,
        key: String,
        scope: CoroutineScope,
        events: Flow<HostEvent>,
        newOperationId: () -> String = { UUID.randomUUID().toString() },
        timing: CharacterSessionTiming = CharacterSessionTiming(),
    ): CharacterSession = CharacterSession(services, site, key, scope, events, newOperationId, timing)
}

/**
 * One open character: it loads the sheet, sends changes one at a time and says
 * what is saved, as the website's character session does for commands.
 *
 * A change is built when it is sent, on the newest saved revision: a
 * [PlayChange], or an edit of the newest raw inputs (so edits made in a row
 * build on each other). A change whose answer is lost is kept exactly as sent
 * (operation ID, revision and body) and sent again until an answer settles it;
 * the service recognises its last operation, so a repeat never applies twice.
 * Every later change waits behind it. Changes made elsewhere are followed
 * through [events] while nothing is waiting.
 *
 * [close] stops the session. Changes not yet sent are dropped. A request
 * already on its way still reaches the host and may be saved there; its answer
 * is ignored, and the next load shows the outcome.
 */
class CharacterSession internal constructor(
    private val services: AddonServices,
    private val site: Site,
    val key: String,
    scope: CoroutineScope,
    events: Flow<HostEvent>,
    private val newOperationId: () -> String,
    private val timing: CharacterSessionTiming,
) {
    private sealed interface Change {
        class Play(val change: PlayChange) : Change

        class Edit(val edit: (JsonObject) -> JsonObject) : Change
    }

    /** A change as sent; [resent] once its first answer was lost, [reapplied] once an edit met a conflict. */
    private data class Attempt(
        val change: Change,
        val request: CharacterRequest,
        val resent: Boolean = false,
        val reapplied: Boolean = false,
        val failures: Int = 0,
    )

    private sealed interface Command {
        class Submit(val change: Change) : Command

        data object Retry : Command

        data object Discard : Command

        data object Reload : Command

        /** Something changed on the host (an event); load once the session is idle and the pause is over. */
        data object Changed : Command

        data object ChangedDue : Command
    }

    private val _state = MutableStateFlow(CharacterSessionState())
    val state: StateFlow<CharacterSessionState> = _state.asStateFlow()

    private val noticeChannel = Channel<CharacterNotice>(Channel.UNLIMITED)

    /** One-shot notices, for one collector. */
    val notices: Flow<CharacterNotice> = noticeChannel.receiveAsFlow()

    private val commands = Channel<Command>(Channel.UNLIMITED)

    // Owned by the session's loop.
    private val queue = ArrayDeque<Change>()
    private var held: Attempt? = null
    /** A load was asked for (an event, a discard, a reload); [forceLoad] takes its answer even if unchanged. */
    private var loadDue = false
    private var forceLoad = false

    /** The sheet shown came without an evaluation (a repeat or a conflict), so a load should bring one. */
    private var evaluationStale = false
    private var retryTimer: Job? = null
    private var eventTimer: Job? = null
    private val job: Job

    init {
        job = scope.launch {
            launch { events.collect { if (concerns(it)) commands.trySend(Command.Changed) } }
            run(this)
        }
    }

    /** Sends [change] after the changes before it; false (nothing queued) while writing is not possible. */
    fun play(change: PlayChange): Boolean = submit(Change.Play(change))

    /**
     * Saves the inputs [edit] makes from the newest saved inputs, after the
     * changes before it; an edit that returns its input sends nothing. False
     * (nothing queued) while writing is not possible.
     */
    fun edit(edit: (JsonObject) -> JsonObject): Boolean = submit(Change.Edit(edit))

    /** Sends a change whose answer was lost again now. */
    fun retry() {
        commands.trySend(Command.Retry)
    }

    /**
     * Gives up a change whose answer was lost and every change waiting behind
     * it (they were made on a sheet that may no longer be the saved one), then
     * loads the character. New changes are taken once the load is done.
     */
    fun discard() {
        commands.trySend(Command.Discard)
    }

    /** Loads the character again, for example after a failed load or signing in again; waits for pending changes. */
    fun reload() {
        commands.trySend(Command.Reload)
    }

    fun close() {
        job.cancel()
        commands.close()
        noticeChannel.close()
    }

    private fun submit(change: Change): Boolean {
        if (!_state.value.canWrite) return false
        _state.update { it.copy(pending = it.pending + 1) }
        if (commands.trySend(Command.Submit(change)).isFailure) {
            _state.update { it.copy(pending = it.pending - 1) }
            return false
        }
        return true
    }

    private fun concerns(event: HostEvent): Boolean = when (event) {
        is HostEvent.Hello, is HostEvent.Reset, is HostEvent.CampaignRestored -> true
        is HostEvent.AddonDataChanged -> services.binding(site, CharacterService.CONTRACT)?.provider?.addonId?.let(event::isFrom) == true
        is HostEvent.CampaignChanged, is HostEvent.Other -> false
    }

    private suspend fun run(scope: CoroutineScope) {
        load(LoadMode.FIRST)
        for (command in commands) {
            handle(command, scope)
            drain(scope)
        }
    }

    private suspend fun handle(command: Command, scope: CoroutineScope) {
        when (command) {
            is Command.Submit -> if (_state.value.canWrite) queue.addLast(command.change) else finish()
            Command.Retry -> held?.let { resend(it, scope) }
            Command.Discard -> if (held != null) discard(scope)
            Command.Reload -> if (_state.value.load is SheetLoad.Sheet) {
                loadDue = true
                forceLoad = true
            } else {
                load(LoadMode.FIRST)
            }
            Command.Changed -> if (eventTimer?.isActive != true) {
                eventTimer = scope.launch {
                    delay(timing.eventPauseMs)
                    commands.trySend(Command.ChangedDue)
                }
            }
            Command.ChangedDue -> loadDue = true
        }
    }

    /** Gives up the held change and everything behind it, and loads before taking new changes. */
    private suspend fun discard(scope: CoroutineScope) {
        held = null
        retryTimer?.cancel()
        finish()
        dropQueue()
        _state.update { it.copy(reloading = true) }
        // Changes sent in before the discard wait in the channel too; other commands run after the load.
        val later = mutableListOf<Command>()
        while (true) {
            val command = commands.tryReceive().getOrNull() ?: break
            if (command is Command.Submit) finish() else later += command
        }
        load(LoadMode.EXPLICIT)
        _state.update { it.copy(reloading = false) }
        for (command in later) handle(command, scope)
    }

    /** Sends queued changes, then a due load, while nothing is held. */
    private suspend fun drain(scope: CoroutineScope) {
        while (held == null) {
            // Changes the player made meanwhile go first.
            while (true) {
                val command = commands.tryReceive().getOrNull() ?: break
                handle(command, scope)
                if (held != null) return
            }
            val next = queue.removeFirstOrNull()
            if (next != null) {
                send(next, scope)
                continue
            }
            if (_state.value.save !is SaveState.Saved) _state.update { it.copy(save = SaveState.Saved) }
            // One load per pass: a load that fails waits for the next command instead of repeating at once.
            if (loadDue || evaluationStale) {
                val mode = if (forceLoad) LoadMode.EXPLICIT else LoadMode.REFRESH
                loadDue = false
                forceLoad = false
                load(mode)
            }
            return
        }
    }

    private suspend fun send(change: Change, scope: CoroutineScope) {
        val answer = _state.value.answer
        if (answer?.state == null || !_state.value.canWrite) return finish()
        val request = try {
            build(change, answer) ?: return finish()
        } catch (error: IllegalArgumentException) {
            // The received inputs did not have what the edit changes.
            notice(CharacterNotice.NotSaved(ServiceFailure.Failed(HostFailure(HostFailure.Reason.MALFORMED, error.message.orEmpty()))))
            return finish()
        }
        attempt(Attempt(change, request), scope)
    }

    /** The request for [change] on [answer]'s saved state, or null when an edit changes nothing. */
    private fun build(change: Change, answer: CharacterAnswer): CharacterRequest? {
        val state = checkNotNull(answer.state)
        return when (change) {
            is Change.Play -> CharacterRequest.play(key, answer.revision, newOperationId(), change.change)
            is Change.Edit -> {
                val edited = change.edit(state.inputs)
                if (edited == state.inputs) null else CharacterRequest.build(key, answer.revision, newOperationId(), edited)
            }
        }
    }

    private suspend fun resend(attempt: Attempt, scope: CoroutineScope) {
        retryTimer?.cancel()
        held = null
        attempt(attempt.copy(resent = true), scope)
    }

    private suspend fun attempt(attempt: Attempt, scope: CoroutineScope) {
        val save = _state.value.save
        _state.update { it.copy(save = if (attempt.resent && save is SaveState.NotSaved) save.copy(retrying = true) else SaveState.Saving) }
        // An unreadable answer is an uncertain failure: the change may have been saved, so it is kept.
        when (val result = ask(attempt.request)) {
            is ServiceResult.Failure -> failed(attempt, result.failure, scope)
            is ServiceResult.Success -> answered(attempt, result.value, scope)
        }
    }

    private suspend fun answered(attempt: Attempt, answer: CharacterAnswer, scope: CoroutineScope) {
        when (answer.status) {
            CharacterStatus.READY -> {
                if (answer.state != null) adopt(answer) else evaluationStale = true
                finish()
            }
            CharacterStatus.INVALID -> {
                notice(CharacterNotice.Refused(answer.message))
                finish()
            }
            CharacterStatus.CONFLICT -> {
                if (answer.state != null) adopt(answer)
                val edit = attempt.change as? Change.Edit
                when {
                    attempt.resent -> notice(CharacterNotice.CouldNotConfirm)
                    edit != null && !attempt.reapplied && answer.state != null && _state.value.load is SheetLoad.Sheet -> {
                        // Nothing was saved: make the same edit on the inputs saved elsewhere.
                        val request = try {
                            build(edit, answer)
                        } catch (error: IllegalArgumentException) {
                            null
                        }
                        if (request != null) return attempt(Attempt(edit, request, reapplied = true), scope)
                    }
                    else -> notice(CharacterNotice.ChangedElsewhere)
                }
                finish()
            }
            CharacterStatus.RULES_CHANGED, CharacterStatus.UNAVAILABLE -> {
                if (answer.state != null) adopt(answer)
                notice(if (answer.status == CharacterStatus.RULES_CHANGED) CharacterNotice.RulesChanged else CharacterNotice.RulesUnavailable)
                finish()
                dropQueue()
            }
            CharacterStatus.TOO_LARGE -> {
                notice(CharacterNotice.TooLarge(answer.message))
                finish()
            }
        }
    }

    private fun failed(attempt: Attempt, failure: ServiceFailure, scope: CoroutineScope) {
        val definite = when (failure) {
            is ServiceFailure.InvalidRequest, ServiceFailure.Forbidden, ServiceFailure.NotFound, ServiceFailure.MethodNotFound,
            ServiceFailure.NotOffered, ServiceFailure.HostTooOld, ServiceFailure.SignedOut,
            -> true
            ServiceFailure.Conflict, ServiceFailure.StaleBinding, ServiceFailure.RateLimited, ServiceFailure.ServiceUnavailable,
            ServiceFailure.DeadlineExceeded, is ServiceFailure.Failed,
            -> false
        }
        if (!definite) {
            val kept = attempt.copy(failures = attempt.failures + 1)
            held = kept
            _state.update { it.copy(save = SaveState.NotSaved(failure, retrying = false)) }
            val pause = timing.retryDelaysMs[minOf(kept.failures, timing.retryDelaysMs.size) - 1]
            retryTimer = scope.launch {
                delay(pause)
                commands.trySend(Command.Retry)
            }
            return
        }
        // This request did nothing; after a lost answer, the first one may still have been saved.
        notice(
            when {
                attempt.resent -> CharacterNotice.CouldNotConfirm
                failure == ServiceFailure.Forbidden -> CharacterNotice.Forbidden
                failure == ServiceFailure.SignedOut -> null
                else -> CharacterNotice.NotSaved(failure)
            },
        )
        finish()
        if (failure == ServiceFailure.SignedOut) {
            _state.update { it.copy(signedOut = true) }
            dropQueue()
        }
    }

    private enum class LoadMode {
        /** The first load, or one after a failed load: it decides the load outcome. */
        FIRST,

        /** Asked for while a sheet is shown: takes the answer even if unchanged. */
        EXPLICIT,

        /** After an event or a change without evaluation: takes only an answer that brings something new. */
        REFRESH,
    }

    /**
     * Loads the character. Once a sheet is shown, a failed load keeps it (only
     * an ended session is noted), and a refresh takes only an answer that
     * differs in revision, status or changed rules, or brings a missing
     * evaluation.
     */
    private suspend fun load(mode: LoadMode) {
        if (mode == LoadMode.FIRST) _state.update { it.copy(load = SheetLoad.Loading) }
        val load = CharacterRequest.load(key)
        var result = ask(load)
        // A load changes nothing, so after a provider update it is simply asked again on the new binding.
        if (result is ServiceResult.Failure && result.failure == ServiceFailure.StaleBinding) result = ask(load)
        val answer = when (result) {
            is ServiceResult.Failure -> return loadFailed(result.failure)
            is ServiceResult.Success -> result.value
        }
        val current = _state.value.answer
        val differs = current == null || answer.revision != current.revision || answer.status != current.status ||
            answer.rulesChanged != current.rulesChanged
        if (mode == LoadMode.REFRESH && !differs && !evaluationStale && !_state.value.signedOut) return
        if (answer.state == null) {
            evaluationStale = false
            _state.update { it.copy(load = SheetLoad.NoSheet, answer = answer, evaluation = answer.evaluation, signedOut = false) }
        } else {
            adopt(answer)
            _state.update { it.copy(signedOut = false) }
        }
    }

    private fun loadFailed(failure: ServiceFailure) {
        if (failure == ServiceFailure.SignedOut) _state.update { it.copy(signedOut = true) }
        if (_state.value.load is SheetLoad.Sheet) return
        val outcome = when (failure) {
            ServiceFailure.NotOffered -> SheetLoad.NotOffered(hostTooOld = false)
            ServiceFailure.HostTooOld -> SheetLoad.NotOffered(hostTooOld = true)
            ServiceFailure.SignedOut -> SheetLoad.SignedOut
            ServiceFailure.Forbidden -> SheetLoad.Forbidden
            else -> SheetLoad.Failed(failure)
        }
        _state.update { it.copy(load = outcome) }
    }

    /**
     * Takes [answer]'s saved state as the sheet, with its evaluation or the
     * last one until a load brings a new one. The screens' view is read here,
     * once per answer and off the main thread; a sheet that cannot be read
     * fails the load and so stops writing.
     */
    private suspend fun adopt(answer: CharacterAnswer) {
        evaluationStale = answer.evaluation == null
        val evaluation = answer.evaluation ?: _state.value.evaluation
        val load = try {
            SheetLoad.Sheet(withContext(Dispatchers.Default) { CharacterSheet.read(answer, evaluation) })
        } catch (error: HostFormatException) {
            SheetLoad.Failed(malformed(error))
        }
        _state.update { it.copy(load = load, answer = answer, evaluation = evaluation) }
    }

    /** One call, with its answer read off the main thread: answers are large. */
    private suspend fun ask(request: CharacterRequest): ServiceResult<CharacterAnswer> = withContext(Dispatchers.Default) {
        when (val result = services.call(site, CharacterService.CONTRACT, request.method, request.params)) {
            is ServiceResult.Failure -> result
            is ServiceResult.Success -> try {
                ServiceResult.Success(CharacterAnswer.parse(result.value, key))
            } catch (error: HostFormatException) {
                ServiceResult.Failure(malformed(error))
            }
        }
    }

    private fun malformed(error: HostFormatException) = ServiceFailure.Failed(HostFailure(HostFailure.Reason.MALFORMED, error.message.orEmpty()))

    /** One change is settled (saved, refused or dropped). */
    private fun finish() {
        _state.update { it.copy(pending = (it.pending - 1).coerceAtLeast(0)) }
    }

    /** Writing stopped: the waiting changes cannot be saved. */
    private fun dropQueue() {
        repeat(queue.size) { finish() }
        queue.clear()
    }

    private fun notice(notice: CharacterNotice?) {
        if (notice != null) noticeChannel.trySend(notice)
    }
}
