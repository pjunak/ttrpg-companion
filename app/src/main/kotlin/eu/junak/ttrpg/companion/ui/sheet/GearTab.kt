package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.Scale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.PlayChange
import eu.junak.ttrpg.companion.core.model.character.SheetGear
import eu.junak.ttrpg.companion.ui.components.GlanceCard
import eu.junak.ttrpg.companion.ui.components.GlanceCount
import eu.junak.ttrpg.companion.ui.components.GlanceMeter
import eu.junak.ttrpg.companion.ui.components.GlanceRow
import eu.junak.ttrpg.companion.ui.components.GlanceTile
import eu.junak.ttrpg.companion.ui.components.LinkRow
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * The Gear tab: its glance card (carried weight, attuned items and coins),
 * quick-use items with Use, what is in hand, worn and carried, and the way to
 * all equipment on the website.
 * Only quick use changes the sheet; the rest is read here and edited there.
 */
@Composable
fun GearTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    val gear = sheet.gear
    val carried = carriedItems(gear.items)
    val worn = wornItems(gear)
    val hands = listOfNotNull(gear.mainHand?.let { it to R.string.sheet_main_hand }, gear.offHand?.let { it to R.string.sheet_off_hand })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        item(key = "glance") { GearGlance(gear) }
        if (gear.quickUse.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_quick_use)) }
            items(gear.quickUse, key = { "quick:" + it.itemId }) { item ->
                ListRow(item.name, subtitle = itemPlace(item.container, item.quantity)) {
                    OutlinedButton(onClick = { actions.play(PlayChange.ConsumeItem(item.itemId)) }, enabled = actions.canWrite && canUse(item)) {
                        Text(stringResource(R.string.sheet_use))
                    }
                }
            }
        }
        if (hands.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_in_hand)) }
            items(hands, key = { "hand:" + it.second }) { (held, hand) ->
                ListRow(held.name, compact = true) { RowValue(stringResource(hand)) }
            }
        }
        if (worn.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_worn)) }
            items(worn, key = { "worn:" + it.id }) { item ->
                ListRow(item.name, compact = true) { RowValue(itemPlace(item.container, item.quantity)) }
            }
        }
        if (carried.isNotEmpty()) item { SectionHeader(stringResource(R.string.sheet_carried)) }
        items(carried, key = { "item:" + it.id }) { item ->
            ListRow(item.name, compact = true) { RowValue(itemPlace(item.container, item.quantity)) }
        }
        item {
            LinkRow(stringResource(R.string.sheet_all_equipment), actions.openWebsite, subtitle = stringResource(R.string.sheet_all_equipment_text))
        }
    }
}

/**
 * The carried weight against what the character can carry, the attuned items
 * against the limit, and the five coins, zeros dimmed (a character without
 * money shows five zeros). A box the sheet has no value for is left out.
 */
@Composable
private fun GearGlance(gear: SheetGear) {
    val spacing = LocalSpacing.current
    val locale = LocalConfiguration.current.locales[0]
    val share = weightShare(gear.carriedPounds, gear.capacityPounds)
    val attunement = gear.attunement
    GlanceCard {
        if (share != null || attunement != null) {
            GlanceRow {
                if (share != null) {
                    val carried = formatPounds(gear.carriedPounds ?: 0.0, locale)
                    val capacity = formatPounds(gear.capacityPounds ?: 0.0, locale)
                    GlanceMeter(
                        Icons.Outlined.Scale,
                        stringResource(R.string.sheet_carried),
                        stringResource(R.string.sheet_weight, carried, capacity),
                        share,
                        stringResource(R.string.sheet_weight_description, carried, capacity),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                if (attunement != null) {
                    GlanceTile(
                        Icons.Outlined.Diamond,
                        countOf(attunement.count, attunement.limit),
                        stringResource(R.string.sheet_attuned),
                        stringResource(R.string.sheet_attuned_description, attunement.count, attunement.limit),
                        (if (share != null) Modifier.widthIn(min = spacing.glanceSide) else Modifier.weight(1f)).fillMaxHeight(),
                    )
                }
            }
        }
        GlanceRow {
            coinStrip(gear.coins).forEach { coin ->
                val name = coinName(coin.coin)?.let { stringResource(it) } ?: coin.coin
                GlanceCount(
                    coin.amount.toString(),
                    coin.coin.uppercase(locale),
                    stringResource(R.string.sheet_coin_description, name, coin.amount.toString()),
                    Modifier.weight(1f).fillMaxHeight(),
                    dimmed = coin.amount == 0L,
                )
            }
        }
    }
}

/** "Backpack · × 6", or "× 4" outside a container. */
@Composable
private fun itemPlace(container: String?, quantity: Int): String =
    listOfNotNull(container, stringResource(R.string.sheet_quantity, quantity)).joinToString(" · ")

/** A row's read-only value at its end, such as "Main hand". */
@Composable
internal fun RowValue(text: String) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
