package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.ui.components.LinkRow
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.SwitchRow
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * The More tab: features with where they come from, the "My character"
 * switch (a preference of this device, so it works whatever the sheet can
 * do), and the way to the character on the website.
 */
@Composable
fun MoreTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        if (sheet.features.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_features)) }
            items(sheet.features, key = { "feature:" + it.id }) { feature ->
                ListRow(feature.name, compact = true) {
                    featureSourceLabel(feature.source).takeIf(String::isNotEmpty)?.let { RowValue(it) }
                }
            }
        }
        item {
            SectionHeader(stringResource(R.string.sheet_this_character))
            SwitchRow(
                title = stringResource(R.string.sheet_my_character),
                checked = actions.myCharacter,
                onCheckedChange = actions.setMyCharacter,
                subtitle = stringResource(R.string.sheet_my_character_text, actions.characterName),
            )
            LinkRow(stringResource(R.string.sheet_open_website), actions.openWebsite, subtitle = stringResource(R.string.sheet_open_website_text))
        }
    }
}
