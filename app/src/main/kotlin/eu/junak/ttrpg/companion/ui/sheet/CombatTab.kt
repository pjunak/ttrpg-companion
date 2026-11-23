package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.Attack
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.FeatureToggle
import eu.junak.ttrpg.companion.core.model.character.HitDice
import eu.junak.ttrpg.companion.core.model.character.LimitedUse
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.RestLength
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.PipRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.SwitchRow
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.NumberStyles
import java.util.UUID

/**
 * The Combat tab: its glance card (hit points, Armor, Speed, Initiative,
 * Inspired, concentration and conditions), then attacks, limited uses with
 * the feature switches, hit dice and rests. A section with nothing in it is
 * left out; every change follows [SheetActions.canWrite].
 */
@Composable
fun CombatTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    val combat = sheet.combat
    // What is open, by key, so it shows the newest sheet and survives rotation.
    var pool by rememberSaveable { mutableStateOf<String?>(null) }
    var hitDie by rememberSaveable { mutableStateOf<String?>(null) }
    var rest by rememberSaveable { mutableStateOf<RestLength?>(null) }
    var keypad by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        item(key = "glance") { CombatGlance(sheet.header, actions, onHitPoints = { keypad = true }, onAddCondition = { picker = true }) }
        if (combat.attacks.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_attacks)) }
            items(combat.attacks) { AttackRow(it) }
        }
        if (combat.limitedUses.isNotEmpty() || combat.toggles.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_limited_uses)) }
            items(combat.limitedUses, key = { "use:" + it.key }) { use -> UseRow(use, actions) { pool = use.key } }
            items(combat.toggles, key = { "toggle:" + it.key }) { ToggleRow(it, combat.limitedUses, actions) }
        }
        if (combat.hitDice.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_hit_dice)) }
            items(combat.hitDice, key = { "dice:" + it.key }) { dice -> HitDiceRow(dice, actions.canWrite) { hitDie = dice.key } }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(top = spacing.md - spacing.xs), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                OutlinedButton(onClick = { rest = RestLength.SHORT }, enabled = actions.canWrite, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sheet_short_rest))
                }
                OutlinedButton(onClick = { rest = RestLength.LONG }, enabled = actions.canWrite, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.sheet_long_rest))
                }
            }
        }
    }

    combat.limitedUses.firstOrNull { it.key == pool }?.let { PoolSheet(it, actions) { pool = null } }
    combat.hitDice.firstOrNull { it.key == hitDie }?.let { HitDieSheet(it, actions) { hitDie = null } }
    rest?.let { length -> RestDialog(length, onRest = { actions.play(PlayChange.Rest(length)) }) { rest = null } }
    if (keypad) {
        HitPointKeypad(
            header = sheet.header,
            enabled = actions.canWrite,
            onChange = { change ->
                actions.play(change)
                keypad = false
            },
            onDismiss = { keypad = false },
        )
    }
    if (picker) {
        ConditionPicker(
            choices = conditionChoices(sheet.conditionOptions, sheet.header.conditions),
            onPick = { id, level ->
                actions.edit { CharacterInputs.setCondition(it, id, level) }
                picker = false
            },
            onDismiss = { picker = false },
        )
    }
}

@Composable
private fun AttackRow(attack: Attack) {
    val resources = LocalResources.current
    val summary = attackSummary(
        attack,
        range = { resources.getString(R.string.sheet_attack_range, it) },
        reach = { resources.getString(R.string.sheet_attack_reach, it.toString()) },
    )
    val toHit = signed(attack.toHit)
    val description = stringResource(R.string.sheet_to_hit, toHit)
    ListRow(attack.name, subtitle = summary) {
        Text(
            toHit,
            style = NumberStyles.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clearAndSetSemantics { contentDescription = description },
        )
    }
}

/** Spends [count] uses (gives them back when negative), counted from the newest saved inputs. */
private fun SheetActions.spend(use: LimitedUse, count: Int) = edit { CharacterInputs.spendResource(it, use.key, count, use.maximum) }

@Composable
private fun UseRow(use: LimitedUse, actions: SheetActions, onSpendAmount: () -> Unit) {
    val left = stringResource(R.string.sheet_left, use.left, use.maximum)
    ListRow(use.name, subtitle = left) {
        when (useShape(use)) {
            UseShape.PIPS -> {
                PipRow(
                    total = use.maximum,
                    left = use.left,
                    onGiveBack = if (actions.canWrite && canSpend(use, -1)) ({ actions.spend(use, -1) }) else null,
                    giveBackLabel = stringResource(R.string.sheet_give_back_one, use.name),
                )
                OutlinedButton(onClick = { actions.spend(use, 1) }, enabled = actions.canWrite && canSpend(use, 1)) {
                    Text(stringResource(R.string.sheet_use))
                }
            }
            UseShape.AMOUNT -> OutlinedButton(onClick = onSpendAmount, enabled = actions.canWrite && (use.left > 0 || use.spent > 0)) {
                Text(stringResource(R.string.sheet_spend))
            }
        }
    }
}

@Composable
private fun ToggleRow(toggle: FeatureToggle, uses: List<LimitedUse>, actions: SheetActions) = SwitchRow(
    title = toggle.name,
    checked = toggle.active,
    onCheckedChange = { actions.play(PlayChange.ToggleFeature(toggle.key, it)) },
    subtitle = toggleSpends(toggle, uses)?.let { stringResource(R.string.sheet_toggle_spends, it) },
    enabled = actions.canWrite && toggleUsable(toggle),
)

@Composable
private fun HitDiceRow(dice: HitDice, canWrite: Boolean, onSpend: () -> Unit) {
    ListRow(dice.die, subtitle = stringResource(R.string.sheet_left, dice.left, dice.maximum)) {
        if (dice.maximum in 1..PIP_LIMIT) PipRow(dice.maximum, dice.left, onGiveBack = null, giveBackLabel = "")
        OutlinedButton(onClick = onSpend, enabled = canWrite && canSpendHitDie(dice)) { Text(stringResource(R.string.sheet_spend)) }
    }
}

/** Spends an amount from a large pool such as Lay on Hands, or gives an amount back after a mis-tap. */
@Composable
private fun PoolSheet(use: LimitedUse, actions: SheetActions, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var number by rememberSaveable(use.key) { mutableStateOf("") }
    val amount = keypadAmount(number) ?: 0
    NumberPadSheet(number, { number = it }, stringResource(R.string.sheet_left, use.left, use.maximum), onDismiss, title = use.name) {
        ActionKey(R.string.sheet_spend, colors.primaryContainer, colors.onPrimaryContainer, colors.primary, actions.canWrite && canSpend(use, amount), Modifier.weight(1f)) {
            actions.spend(use, amount)
            onDismiss()
        }
        ActionKey(R.string.sheet_give_back, colors.surfaceContainer, colors.onSurface, colors.outline, actions.canWrite && canSpend(use, -amount), Modifier.weight(1f)) {
            actions.spend(use, -amount)
            onDismiss()
        }
    }
}

/** Spends a hit die: the number rolled at the table, or a roll made on the phone. Each spend is a new roll. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HitDieSheet(dice: HitDice, actions: SheetActions, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val resources = LocalResources.current
    val size = dieSize(dice.die) ?: return
    val enabled = actions.canWrite && canSpendHitDie(dice)
    fun spend(result: Int) {
        actions.play(PlayChange.SpendHitDie(dice.key, result, UUID.randomUUID().toString()))
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = spacing.md).padding(bottom = spacing.lg), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
            Text(stringResource(R.string.sheet_hit_die_title, dice.die), style = MaterialTheme.typography.titleLarge, color = colors.primary)
            Text(stringResource(R.string.sheet_hit_die_text), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            (1..size).chunked(DIE_FACES_PER_ROW).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    row.forEach { face ->
                        KeySurface({ spend(face) }, Modifier.weight(1f), enabled) { Text(face.toString(), style = MaterialTheme.typography.headlineSmall) }
                    }
                    repeat(DIE_FACES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row {
                ActionKey(R.string.sheet_roll_on_phone, colors.primaryContainer, colors.onPrimaryContainer, colors.primary, enabled, Modifier.weight(1f)) {
                    val result = rollDie(size)
                    spend(result)
                    actions.announce(resources.getString(R.string.sheet_rolled, dice.die, result))
                }
            }
        }
    }
}

private const val DIE_FACES_PER_ROW = 4

@Composable
private fun RestDialog(length: RestLength, onRest: () -> Unit, onDismiss: () -> Unit) {
    val short = length == RestLength.SHORT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (short) R.string.sheet_short_rest_title else R.string.sheet_long_rest_title)) },
        text = { Text(stringResource(if (short) R.string.sheet_short_rest_text else R.string.sheet_long_rest_text)) },
        confirmButton = {
            TextButton(onClick = {
                onRest()
                onDismiss()
            }) { Text(stringResource(R.string.sheet_rest)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
