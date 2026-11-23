package eu.junak.ttrpg.companion.ui.sheet

import android.content.res.Resources
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.character.CharacterNotice
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import eu.junak.ttrpg.companion.ui.campaign.hostFailureText
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.Banner
import eu.junak.ttrpg.companion.ui.components.BannerButton
import eu.junak.ttrpg.companion.ui.components.BannerTone
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.LocalMarkDetails
import eu.junak.ttrpg.companion.ui.components.MarkedValue
import eu.junak.ttrpg.companion.ui.components.WholeWordText
import eu.junak.ttrpg.companion.ui.components.relativeTime
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

/**
 * A character's sheet: the top bar with the name, species and classes and
 * the save badge, any banner, then the tab chosen in the bottom bar. Each tab
 * starts with its own glance card; [tabBody] draws a tab from the sheet and
 * what it can change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenWebsite: (path: String) -> Unit,
    tabBody: @Composable (SheetTab, CharacterSheet, SheetActions) -> Unit = { tab, sheet, actions -> SheetTabBody(tab, sheet, actions) },
    viewModel: SheetViewModel = hiltViewModel(),
) {
    if (viewModel.site == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val myCharacter by viewModel.myCharacter.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    LaunchedEffect(viewModel, resources) { viewModel.notices.collect { snackbars.showSnackbar(noticeText(resources, it)) } }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.screenStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.screenStarted() }
    var tab by rememberSaveable { mutableStateOf(SheetTab.COMBAT) }
    // The marked value whose causes are open; tapping a marked value sets it.
    var causes by remember { mutableStateOf<MarkedValue?>(null) }
    val website = { onOpenWebsite(CampaignPage.CHARACTERS.recordPath(viewModel.key)) }
    val sheet = (state.load as? SheetLoad.Sheet)?.sheet

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.primary)
                        if (sheet != null) {
                            Text(
                                classLine(sheet.header),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = { BackButton(onBack) },
                actions = { if (sheet != null) SaveBadgeView(saveBadge(state)) },
            )
        },
        bottomBar = { if (sheet != null) SheetTabs(tab) { tab = it } },
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            when (val load = state.load) {
                SheetLoad.Loading -> CenteredMessage(null, loading = true)
                is SheetLoad.Sheet -> {
                    KeepScreenOn()
                    SheetBannerView(sheetBanner(state), name.orEmpty(), state.refreshing, viewModel, website)
                    val actions = SheetActions(
                        canWrite = state.canWrite,
                        dimmed = sheetDimmed(state),
                        play = viewModel::play,
                        edit = viewModel::edit,
                        openWebsite = website,
                        announce = { message -> scope.launch { snackbars.showSnackbar(message) } },
                        characterName = name.orEmpty(),
                        myCharacter = myCharacter,
                        setMyCharacter = viewModel::setMyCharacter,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        CompositionLocalProvider(LocalMarkDetails provides { causes = it }) { tabBody(tab, load.sheet, actions) }
                    }
                }
                SheetLoad.NoSheet -> NoSheet(name.orEmpty(), website)
                SheetLoad.SignedOut -> CenteredMessage(stringResource(R.string.sheet_signed_out)) {
                    TextButton(onClick = onSignIn) { Text(stringResource(R.string.action_sign_in_again)) }
                }
                SheetLoad.Forbidden -> CenteredMessage(stringResource(R.string.sheet_forbidden))
                is SheetLoad.NotOffered -> CenteredMessage(
                    stringResource(if (load.hostTooOld) R.string.sheet_host_too_old else R.string.sheet_not_offered),
                )
                is SheetLoad.Failed -> CenteredMessage(stringResource(R.string.sheet_failed, failureText(load.failure))) {
                    TextButton(onClick = viewModel::reload) { Text(stringResource(R.string.action_retry)) }
                }
            }
        }
    }
    causes?.let { MarkCausesSheet(it) { causes = null } }
}

/** The body of [tab]. */
@Composable
fun SheetTabBody(tab: SheetTab, sheet: CharacterSheet, actions: SheetActions) {
    when (tab) {
        SheetTab.COMBAT -> CombatTab(sheet, actions)
        SheetTab.ABILITIES -> AbilitiesTab(sheet)
        SheetTab.SPELLS -> SpellsTab(sheet, actions)
        SheetTab.GEAR -> GearTab(sheet, actions)
        SheetTab.MORE -> MoreTab(sheet, actions)
    }
}

/** Keeps the phone's screen on while the sheet is shown, as a sheet on the table would be. */
@Composable
private fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
}

@Composable
private fun SaveBadgeView(badge: SaveBadge) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val (icon, text) = when (badge) {
        SaveBadge.SAVED -> Icons.Outlined.CloudDone to R.string.sheet_saved
        SaveBadge.SAVING -> Icons.Outlined.CloudSync to R.string.sheet_saving
        SaveBadge.NOT_SAVED -> Icons.Outlined.CloudOff to R.string.sheet_not_saved
        SaveBadge.UPDATING -> Icons.Outlined.CloudSync to R.string.sheet_updating
        SaveBadge.OFFLINE -> Icons.Outlined.CloudOff to R.string.sheet_offline
    }
    val color = if (badge == SaveBadge.NOT_SAVED) colors.error else colors.onSurfaceVariant
    Row(
        Modifier.padding(end = spacing.md - spacing.xs).semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(spacing.iconSmall))
        Text(stringResource(text), style = MaterialTheme.typography.labelMedium, color = color)
    }
}

@Composable
private fun SheetBannerView(banner: SheetBanner?, name: String, refreshing: Boolean, viewModel: SheetViewModel, website: () -> Unit) {
    val spacing = LocalSpacing.current
    val place = Modifier.padding(start = spacing.md, end = spacing.md, top = spacing.md - spacing.xs, bottom = spacing.sm)
    when (banner) {
        is SheetBanner.NotSaved -> Banner(stringResource(R.string.sheet_banner_not_saved), BannerTone.ALERT, place, actionsBelow = true) {
            if (banner.retrying) {
                CircularProgressIndicator(Modifier.size(LocalSpacing.current.progressInline))
            } else {
                BannerButton(stringResource(R.string.action_retry), viewModel::retry)
            }
            BannerButton(stringResource(R.string.action_discard), viewModel::discard)
        }
        is SheetBanner.DeviceCopy -> {
            Banner(stringResource(R.string.sheet_banner_copy_unreachable, relativeTime(banner.receivedAt)), BannerTone.ALERT, place) {
                if (refreshing) {
                    CircularProgressIndicator(Modifier.size(LocalSpacing.current.progressInline))
                } else {
                    BannerButton(stringResource(R.string.action_retry), viewModel::retry)
                }
            }
        }
        SheetBanner.RulesChanged -> Banner(stringResource(R.string.sheet_banner_rules_changed, name), BannerTone.INFO, place) {
            BannerButton(stringResource(R.string.sheet_open), website)
        }
        SheetBanner.RulesUnavailable -> Banner(stringResource(R.string.sheet_banner_rules_unavailable), BannerTone.INFO, place)
        null -> Unit
    }
}

@Composable
private fun SheetTabs(selected: SheetTab, onSelect: (SheetTab) -> Unit) {
    NavigationBar {
        SheetTab.entries.forEach { tab ->
            val (icon, label) = when (tab) {
                SheetTab.COMBAT -> Icons.Outlined.Shield to R.string.sheet_tab_combat
                SheetTab.ABILITIES -> Icons.Outlined.BarChart to R.string.sheet_tab_abilities
                SheetTab.SPELLS -> Icons.Outlined.AutoAwesome to R.string.sheet_tab_spells
                SheetTab.GEAR -> Icons.Outlined.Backpack to R.string.sheet_tab_gear
                SheetTab.MORE -> Icons.Outlined.MoreHoriz to R.string.sheet_tab_more
            }
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(icon, contentDescription = null) },
                label = { WholeWordText(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun NoSheet(name: String, onOpenBuilder: () -> Unit) =
    CenteredMessage(stringResource(R.string.sheet_none_text, name), title = stringResource(R.string.sheet_none_title)) {
        TextButton(onClick = onOpenBuilder) { Text(stringResource(R.string.sheet_open_builder)) }
    }

/** A session notice in the reader's language; the rules' own refusal is shown as the site sent it. */
private fun noticeText(resources: Resources, notice: CharacterNotice): String = when (notice) {
    is CharacterNotice.Refused -> notice.message
    CharacterNotice.ChangedElsewhere -> resources.getString(R.string.sheet_notice_changed_elsewhere)
    CharacterNotice.CouldNotConfirm -> resources.getString(R.string.sheet_notice_could_not_confirm)
    CharacterNotice.RulesChanged -> resources.getString(R.string.sheet_notice_rules_changed)
    CharacterNotice.RulesUnavailable -> resources.getString(R.string.sheet_notice_rules_unavailable)
    is CharacterNotice.TooLarge -> resources.getString(R.string.sheet_notice_too_large)
    CharacterNotice.Forbidden -> resources.getString(R.string.sheet_notice_forbidden)
    is CharacterNotice.NotSaved -> resources.getString(R.string.sheet_notice_not_saved)
}

/** Why the sheet service failed, in the reader's language. */
@Composable
private fun failureText(failure: ServiceFailure): String = when (failure) {
    is ServiceFailure.Failed -> hostFailureText(failure.failure)
    ServiceFailure.ServiceUnavailable -> stringResource(R.string.sheet_failure_unavailable)
    ServiceFailure.DeadlineExceeded -> hostFailureText(HostFailure(HostFailure.Reason.TIMEOUT, ""))
    ServiceFailure.RateLimited -> stringResource(R.string.sheet_failure_rate_limited)
    else -> stringResource(R.string.sheet_failure_refused)
}
