package eu.junak.ttrpg.companion.ui.sheet

import android.content.res.Resources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Backpack
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.character.ActiveCondition
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.SheetHeader
import eu.junak.ttrpg.companion.core.network.HostFailure
import eu.junak.ttrpg.companion.core.network.character.CharacterNotice
import eu.junak.ttrpg.companion.core.network.character.CharacterSessionState
import eu.junak.ttrpg.companion.core.network.character.SheetLoad
import eu.junak.ttrpg.companion.core.network.services.ServiceFailure
import eu.junak.ttrpg.companion.ui.campaign.hostFailureText
import eu.junak.ttrpg.companion.ui.components.AddChip
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.components.Banner
import eu.junak.ttrpg.companion.ui.components.BannerButton
import eu.junak.ttrpg.companion.ui.components.BannerTone
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.ChipTone
import eu.junak.ttrpg.companion.ui.components.RemovableChip
import eu.junak.ttrpg.companion.ui.components.StatChip
import eu.junak.ttrpg.companion.ui.components.ToggleChip
import eu.junak.ttrpg.companion.ui.components.relativeTime
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.LocalVitalColors
import kotlinx.coroutines.launch

/**
 * A character's sheet: a header with hit points, stats, concentration and
 * conditions above the tab chosen in the bottom bar. [tabBody] draws a tab from
 * the sheet and what it can change.
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
    var keypad by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }
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
                    Header(load.sheet.header, state, onHitPoints = { keypad = true }, onAddCondition = { picker = true }, viewModel)
                    val actions = SheetActions(
                        canWrite = state.canWrite,
                        play = viewModel::play,
                        edit = viewModel::edit,
                        openWebsite = website,
                        announce = { message -> scope.launch { snackbars.showSnackbar(message) } },
                        characterName = name.orEmpty(),
                        myCharacter = myCharacter,
                        setMyCharacter = viewModel::setMyCharacter,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) { tabBody(tab, load.sheet, actions) }
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

    if (keypad && sheet != null) {
        HitPointKeypad(
            header = sheet.header,
            enabled = state.canWrite,
            onChange = { change ->
                viewModel.play(change)
                keypad = false
            },
            onDismiss = { keypad = false },
        )
    }
    if (picker && sheet != null) {
        ConditionPicker(
            choices = conditionChoices(sheet.conditionOptions, sheet.header.conditions),
            onPick = { id, level ->
                viewModel.setCondition(id, level)
                picker = false
            },
            onDismiss = { picker = false },
        )
    }
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
private fun Header(
    header: SheetHeader,
    state: CharacterSessionState,
    onHitPoints: () -> Unit,
    onAddCondition: () -> Unit,
    viewModel: SheetViewModel,
) {
    val spacing = LocalSpacing.current
    val canWrite = state.canWrite
    Column(
        Modifier
            .fillMaxWidth()
            .alpha(if (headerDimmed(state)) Emphasis.DIMMED else 1f)
            .padding(start = spacing.md, end = spacing.md, top = spacing.xs, bottom = spacing.md - spacing.xs),
        verticalArrangement = Arrangement.spacedBy(spacing.sm + spacing.xxs),
    ) {
        HitPoints(header, canWrite, onHitPoints)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            StatChip(stringResource(R.string.sheet_ac), header.armorClass.toString())
            StatChip(stringResource(R.string.sheet_speed), header.speed.toString())
            StatChip(stringResource(R.string.sheet_initiative), signed(header.initiative))
            ToggleChip(
                stringResource(R.string.sheet_inspired),
                checked = header.inspiration,
                onCheckedChange = { viewModel.toggleInspiration() },
                enabled = canWrite,
                icon = Icons.Outlined.Star,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            header.concentration?.let {
                RemovableChip(
                    it.name,
                    ChipTone.CONCENTRATION,
                    stringResource(R.string.sheet_concentration_end, it.name),
                    onRemove = viewModel::endConcentration,
                    enabled = canWrite,
                    leading = Icons.Outlined.Psychology,
                )
            }
            header.conditions.forEach { condition ->
                val label = conditionLabel(condition)
                RemovableChip(
                    label,
                    ChipTone.CONDITION,
                    stringResource(R.string.sheet_condition_end, label),
                    onRemove = { viewModel.removeCondition(condition.id) },
                    enabled = canWrite,
                )
            }
            AddChip(stringResource(R.string.sheet_condition_add), Icons.Outlined.Add, onAddCondition, enabled = canWrite)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun conditionLabel(condition: ActiveCondition): String =
    if (condition.level > 1) stringResource(R.string.sheet_condition_level, condition.name, condition.level) else condition.name

@Composable
private fun HitPoints(header: SheetHeader, canWrite: Boolean, onChange: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val vitals = LocalVitalColors.current
    val description = stringResource(R.string.sheet_hp_description, header.hitPoints, header.maxHitPoints) +
        if (header.temporaryHitPoints > 0) ", " + stringResource(R.string.sheet_hp_temporary_description, header.temporaryHitPoints) else ""
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.md - spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(
            Modifier
                .weight(1f)
                .clip(MaterialTheme.shapes.small)
                .clickable(enabled = canWrite, onClick = onChange)
                .clearAndSetSemantics { contentDescription = description },
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(header.hitPoints.toString(), style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold))
                Text(" / ${header.maxHitPoints}", style = MaterialTheme.typography.titleLarge, color = colors.onSurfaceVariant)
                if (header.temporaryHitPoints > 0) {
                    Text(
                        "  +${header.temporaryHitPoints}",
                        style = MaterialTheme.typography.titleMedium,
                        color = vitals.temporary,
                    )
                }
            }
            HitPointBar(header)
        }
        Surface(
            onClick = onChange,
            enabled = canWrite,
            shape = MaterialTheme.shapes.large,
            color = colors.surfaceContainerHigh,
            contentColor = colors.onPrimaryContainer,
            border = BorderStroke(spacing.hairline, colors.outline),
            modifier = Modifier.size(spacing.controlLarge).alpha(if (canWrite) 1f else Emphasis.DIMMED),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Exposure, contentDescription = stringResource(R.string.sheet_hp_change))
            }
        }
    }
}

@Composable
private fun HitPointBar(header: SheetHeader) {
    val vitals = LocalVitalColors.current
    val (current, temporary) = hitPointShares(header)
    Row(
        Modifier
            .fillMaxWidth()
            .height(LocalSpacing.current.meter)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        if (current > 0f) Box(Modifier.fillMaxHeight().weight(current).background(vitals.health))
        if (temporary > 0f) Box(Modifier.fillMaxHeight().weight(temporary).background(vitals.temporary))
        val rest = 1f - current - temporary
        if (rest > 0f) Box(Modifier.fillMaxHeight().weight(rest))
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
                label = { Text(stringResource(label)) },
            )
        }
    }
}

@Composable
private fun NoSheet(name: String, onOpenBuilder: () -> Unit) =
    CenteredMessage(stringResource(R.string.sheet_none_text, name), title = stringResource(R.string.sheet_none_title)) {
        TextButton(onClick = onOpenBuilder) { Text(stringResource(R.string.sheet_open_builder)) }
    }

/** The ± keypad: type an amount, then damage, heal or set temporary hit points. */
@Composable
private fun HitPointKeypad(header: SheetHeader, enabled: Boolean, onChange: (PlayChange) -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val vitals = LocalVitalColors.current
    var number by rememberSaveable { mutableStateOf("") }
    val amount = keypadAmount(number)
    NumberPadSheet(number, { number = it }, stringResource(R.string.sheet_keypad_current, header.hitPoints, header.maxHitPoints), onDismiss) {
        @Composable
        fun Action(action: HitPointAction, label: Int, container: Color, content: Color, border: Color) =
            ActionKey(label, container, content, border, enabled && keypadAllows(action, amount), Modifier.weight(1f)) {
                amount?.let { onChange(hitPointChange(action, it)) }
            }
        Action(HitPointAction.DAMAGE, R.string.sheet_damage, colors.tertiaryContainer, colors.onTertiaryContainer, colors.tertiary)
        Action(HitPointAction.HEAL, R.string.sheet_heal, vitals.healContainer, vitals.onHealContainer, vitals.health)
        Action(HitPointAction.TEMPORARY, R.string.sheet_temporary, vitals.temporaryContainer, vitals.onTemporaryContainer, vitals.temporary)
    }
}

/** Picks a condition to add; one with levels, such as exhaustion, asks for the level next. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConditionPicker(choices: List<ConditionChoice>, onPick: (id: String, level: Int) -> Unit, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    var levelFor by rememberSaveable { mutableStateOf<String?>(null) }
    val levelled = choices.firstOrNull { it.id == levelFor }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            if (levelled == null) stringResource(R.string.sheet_conditions_title) else stringResource(R.string.sheet_condition_level_title, levelled.name),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = spacing.md).padding(bottom = spacing.sm),
        )
        LazyColumn(Modifier.padding(bottom = spacing.lg)) {
            if (levelled == null) {
                items(choices, key = { it.id }) { choice ->
                    ListItem(
                        headlineContent = { Text(choice.name) },
                        supportingContent = choice.level?.let { { Text(stringResource(R.string.sheet_condition_level_option, it)) } },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .clickable(enabled = choice.enabled) { if (choice.levelled) levelFor = choice.id else onPick(choice.id, 1) }
                            .alpha(if (choice.enabled) 1f else Emphasis.DIMMED),
                    )
                }
            } else {
                items((1..levelled.maximumLevel).toList(), key = { it }) { level ->
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.sheet_condition_level_option, level)) },
                        trailingContent = if (level == levelled.level) {
                            { Icon(Icons.Outlined.Star, contentDescription = null) }
                        } else {
                            null
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { onPick(levelled.id, level) },
                    )
                }
            }
        }
    }
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
