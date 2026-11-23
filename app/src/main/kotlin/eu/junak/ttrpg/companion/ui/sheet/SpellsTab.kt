package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.mikepenz.markdown.m3.Markdown
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.CastableSpell
import eu.junak.ttrpg.companion.core.model.character.CharacterInputs
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.SpellCaster
import eu.junak.ttrpg.companion.core.model.character.SpellSlot
import eu.junak.ttrpg.companion.ui.components.Banner
import eu.junak.ttrpg.companion.ui.components.BannerTone
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.ChoiceRow
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.PipRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.theme.ArticleTheme
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * The Spells tab: each casting class's save DC and attack, pact slots, then
 * the spells by level (cantrips first) under their slots, each with Cast. A
 * spent slot's pip gives it back after a mis-tap.
 */
@Composable
fun SpellsTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    val spells = sheet.spells
    if (!hasSpells(spells)) {
        CenteredMessage(stringResource(R.string.sheet_no_spells))
        return
    }
    var casting by rememberSaveable { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        items(spells.casters, key = { "caster:" + it.classId }) { CasterLine(it) }
        items(spells.slots.filter { it.pact }, key = { "pact:" + it.key }) { slot ->
            ListRow(stringResource(R.string.sheet_pact_slots, romanLevel(slot.level)), subtitle = stringResource(R.string.sheet_left, slot.left, slot.maximum)) {
                SlotPips(slot, actions)
            }
        }
        for (section in spellSections(spells)) {
            item(key = "level:" + section.level) {
                SectionHeader(if (section.level == 0) stringResource(R.string.sheet_cantrips) else stringResource(R.string.sheet_spell_level, romanLevel(section.level))) {
                    section.slots.forEach { slot ->
                        Text(stringResource(R.string.sheet_slots_left, slot.left, slot.maximum), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        SlotPips(slot, actions)
                    }
                }
            }
            items(section.spells, key = { "spell:" + it.ref }) { spell ->
                val castable = castOptions(spell, spells.slots, spells.casters).any { it.available }
                SpellRow(spell, actions.canWrite && castable) { casting = spell.ref }
            }
        }
    }
    spells.spells.firstOrNull { it.ref == casting }?.let { CastSheet(it, sheet, actions) { casting = null } }
}

@Composable
private fun CasterLine(caster: SpellCaster) {
    val colors = MaterialTheme.colorScheme
    val strong = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)
    Text(
        buildAnnotatedString {
            append(caster.name + " · " + stringResource(R.string.sheet_save_dc) + " ")
            withStyle(strong) { append(caster.saveDc.toString()) }
            append(" · " + stringResource(R.string.sheet_spell_attack) + " ")
            withStyle(strong) { append(signed(caster.attackBonus)) }
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(top = LocalSpacing.current.sm + LocalSpacing.current.xxs),
    )
}

/** A slot's pips; a spent one gives the slot back, counted from the newest saved inputs. */
@Composable
private fun SlotPips(slot: SpellSlot, actions: SheetActions) = PipRow(
    total = slot.maximum,
    left = slot.left,
    onGiveBack = if (actions.canWrite && slot.left < slot.maximum) ({ actions.edit { CharacterInputs.spendResource(it, slot.key, -1, slot.maximum) } }) else null,
    giveBackLabel = stringResource(R.string.sheet_give_back_slot, romanLevel(slot.level)),
)

@Composable
private fun SpellRow(spell: CastableSpell, castable: Boolean, onCast: () -> Unit) {
    val tags = listOfNotNull(
        stringResource(R.string.sheet_concentration_tag).takeIf { spell.concentration },
        freeCastsLeft(spell)?.let { stringResource(R.string.sheet_free_tag, it.toString()) },
    )
    val line = listOfNotNull(spell.castingTime?.let(::shortCastingTime), spell.range).filter(String::isNotEmpty).joinToString(" · ")
    ListRow(spell.name, subtitle = line.ifEmpty { null }, tags = tags) {
        OutlinedButton(onClick = onCast, enabled = castable) { Text(stringResource(R.string.sheet_cast)) }
    }
}

/**
 * Casts [spell]: its facts, a note when it ends the current concentration,
 * every way to cast it now with what is left, Cast, then the summary, which
 * can be long. The only usable way starts chosen; otherwise the player picks one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CastSheet(spell: CastableSpell, sheet: CharacterSheet, actions: SheetActions, onDismiss: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val options = castOptions(spell, sheet.spells.slots, sheet.spells.casters)
    var chosen by rememberSaveable(spell.ref) { mutableStateOf(preselectedOption(options)?.let(options::indexOf)) }
    val choice = chosen?.let(options::getOrNull)
    val level = if (spell.level == 0) stringResource(R.string.sheet_cantrip) else stringResource(R.string.sheet_spell_level, romanLevel(spell.level))
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = spacing.md).padding(bottom = spacing.lg),
            verticalArrangement = Arrangement.spacedBy(spacing.md - spacing.xs),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Text(spell.name, style = MaterialTheme.typography.titleLarge, color = colors.primary)
                Text(
                    listOfNotNull(level, spell.castingTime, spell.range, spell.duration).filter(String::isNotEmpty).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            concentrationEnded(spell, sheet.header.concentration)?.let {
                Banner(stringResource(R.string.sheet_cast_ends_concentration, it), BannerTone.INFO)
            }
            Text(stringResource(R.string.sheet_cast_how), style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
            options.forEachIndexed { index, option ->
                ChoiceRow(
                    title = optionTitle(option),
                    selected = chosen == index,
                    onSelect = { chosen = index },
                    detail = option.left?.let { stringResource(R.string.sheet_left_short, it) },
                    enabled = option.available,
                )
            }
            ActionKey(
                R.string.sheet_cast,
                colors.primaryContainer,
                colors.onPrimaryContainer,
                colors.primary,
                actions.canWrite && choice?.available == true,
                Modifier.fillMaxWidth(),
            ) {
                choice?.let { actions.play(it.change) }
                onDismiss()
            }
            spell.summary?.let { spellSummary(it, spell.name) }?.takeIf(String::isNotEmpty)?.let { summary ->
                ArticleTheme { Markdown(content = summary) }
            }
        }
    }
}

@Composable
private fun optionTitle(option: CastOption): String {
    val slot = option.level?.let(::romanLevel) ?: option.slotKey.orEmpty()
    val title = when (option.kind) {
        CastKind.FREE -> stringResource(R.string.sheet_cast_free)
        CastKind.AT_WILL -> stringResource(R.string.sheet_cast_at_will)
        CastKind.NO_SLOT -> stringResource(R.string.sheet_cast_no_slot)
        CastKind.SLOT -> stringResource(R.string.sheet_cast_slot, slot)
        CastKind.PACT_SLOT -> stringResource(R.string.sheet_cast_pact, slot)
        CastKind.RITUAL -> stringResource(R.string.sheet_cast_ritual)
    }
    return listOfNotNull(title, option.source).joinToString(" · ")
}
