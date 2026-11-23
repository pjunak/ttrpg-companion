package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import eu.junak.ttrpg.companion.ui.components.LinkRow
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.StatChip
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * The Gear tab: quick-use items with Use, what is in hand and worn, coins, the
 * carried weight and items, and the way to all equipment on the website.
 * Only quick use changes the sheet; the rest is read here and edited there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GearTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    val gear = sheet.gear
    val locale = LocalConfiguration.current.locales[0]
    val carried = carriedItems(gear.items)
    val worn = wornItems(gear)
    val hands = listOfNotNull(gear.mainHand?.let { it to R.string.sheet_main_hand }, gear.offHand?.let { it to R.string.sheet_off_hand })
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
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
        if (gear.coins.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.sheet_coins))
                FlowRow(
                    Modifier.padding(top = spacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(spacing.sm),
                ) {
                    gear.coins.forEach { StatChip(it.coin.uppercase(locale), it.amount.toString()) }
                }
            }
        }
        item {
            SectionHeader(
                stringResource(R.string.sheet_carried),
                trailing = stringResource(R.string.sheet_weight, formatPounds(gear.carriedPounds, locale), formatPounds(gear.capacityPounds, locale)),
            )
        }
        items(carried, key = { "item:" + it.id }) { item ->
            ListRow(item.name, compact = true) { RowValue(itemPlace(item.container, item.quantity)) }
        }
        item {
            LinkRow(stringResource(R.string.sheet_all_equipment), actions.openWebsite, subtitle = stringResource(R.string.sheet_all_equipment_text))
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
