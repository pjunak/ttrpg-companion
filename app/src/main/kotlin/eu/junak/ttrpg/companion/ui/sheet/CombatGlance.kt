package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DoubleArrow
import androidx.compose.material.icons.outlined.Exposure
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.ActiveCondition
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.Concentration
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.SheetHeader
import eu.junak.ttrpg.companion.ui.components.AddChip
import eu.junak.ttrpg.companion.ui.components.ChipTone
import eu.junak.ttrpg.companion.ui.components.GlanceCard
import eu.junak.ttrpg.companion.ui.components.GlanceRow
import eu.junak.ttrpg.companion.ui.components.GlanceTile
import eu.junak.ttrpg.companion.ui.components.RemovableChip
import eu.junak.ttrpg.companion.ui.theme.Emphasis
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.LocalVitalColors

/**
 * The Combat tab's glance card: hit points with the keypad, Armor, Speed,
 * Initiative and the Inspired switch, then concentration and conditions.
 * It is dimmed while the rules changed or are unavailable: readable, not usable.
 */
@Composable
internal fun CombatGlance(header: SheetHeader, actions: SheetActions, onHitPoints: () -> Unit, onAddCondition: () -> Unit) {
    val canWrite = actions.canWrite
    GlanceCard(Modifier.alpha(if (actions.dimmed) Emphasis.DIMMED else 1f)) {
        HitPoints(header, canWrite, onHitPoints)
        GlanceRow {
            val tile = Modifier.weight(1f).fillMaxHeight()
            GlanceTile(
                Icons.Outlined.Shield,
                header.armorClass.toString(),
                stringResource(R.string.sheet_ac),
                stringResource(R.string.sheet_ac_description, header.armorClass),
                tile,
            )
            GlanceTile(
                Icons.Outlined.DoubleArrow,
                header.speed.toString(),
                stringResource(R.string.sheet_speed),
                stringResource(R.string.sheet_speed_description, header.speed),
                tile,
            )
            GlanceTile(
                Icons.Outlined.Bolt,
                signed(header.initiative),
                stringResource(R.string.sheet_initiative),
                stringResource(R.string.sheet_initiative_description, signed(header.initiative)),
                tile,
            )
            GlanceTile(
                if (header.inspiration) Icons.Filled.Star else Icons.Outlined.StarBorder,
                null,
                stringResource(R.string.sheet_inspired),
                stringResource(R.string.sheet_inspired_description),
                tile,
                checked = header.inspiration,
                // Switched on the newest saved inputs, so taps in a row alternate.
                onToggle = { actions.edit { CharacterInputs.setInspiration(it, !CharacterInputs.inspiration(it)) } },
                enabled = canWrite,
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm), verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)) {
            header.concentration?.let { ConcentrationChip(it, actions) }
            header.conditions.forEach { condition ->
                val label = conditionLabel(condition)
                RemovableChip(
                    label,
                    ChipTone.CONDITION,
                    stringResource(R.string.sheet_condition_end, label),
                    onRemove = { actions.edit { CharacterInputs.removeCondition(it, condition.id) } },
                    enabled = canWrite,
                )
            }
            AddChip(stringResource(R.string.sheet_condition_add), Icons.Outlined.Add, onAddCondition, enabled = canWrite)
        }
    }
}

/** The spell being concentrated on; tapping it ends concentration. The Combat and Spells glances both show it. */
@Composable
internal fun ConcentrationChip(concentration: Concentration, actions: SheetActions) = RemovableChip(
    concentration.name,
    ChipTone.CONCENTRATION,
    stringResource(R.string.sheet_concentration_end, concentration.name),
    onRemove = { actions.play(PlayChange.EndConcentration) },
    enabled = actions.canWrite,
    leading = Icons.Outlined.Psychology,
)

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
                    Text("  +${header.temporaryHitPoints}", style = MaterialTheme.typography.titleMedium, color = vitals.temporary)
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

/** The ± keypad: type an amount, then damage, heal or set temporary hit points. */
@Composable
internal fun HitPointKeypad(header: SheetHeader, enabled: Boolean, onChange: (PlayChange) -> Unit, onDismiss: () -> Unit) {
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
internal fun ConditionPicker(choices: List<ConditionChoice>, onPick: (id: String, level: Int) -> Unit, onDismiss: () -> Unit) {
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
