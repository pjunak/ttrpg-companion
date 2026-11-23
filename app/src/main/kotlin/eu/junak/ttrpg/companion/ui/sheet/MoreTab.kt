package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.ui.components.GlanceCard
import eu.junak.ttrpg.companion.ui.components.GlanceFact
import eu.junak.ttrpg.companion.ui.components.GlanceFacts
import eu.junak.ttrpg.companion.ui.components.LinkRow
import eu.junak.ttrpg.companion.ui.components.ListRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.SwitchRow
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing

/**
 * The More tab: its identity card (classes, species and size, background,
 * languages), features with where they come from, the "My character"
 * switch (a preference of this device, so it works whatever the sheet can
 * do), and the way to the character on the website.
 */
@Composable
fun MoreTab(sheet: CharacterSheet, actions: SheetActions) {
    val spacing = LocalSpacing.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        item(key = "glance") { IdentityGlance(sheet) }
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

/** Who the character is, a row for each value the sheet's answer carries. */
@Composable
private fun IdentityGlance(sheet: CharacterSheet) {
    val identity = sheet.identity
    val size = identity.size?.let { size -> sizeName(size)?.let { stringResource(it) } ?: size }
    val facts = listOfNotNull(
        classFact(sheet.header.classes)?.let { GlanceFact(Icons.Outlined.Flag, stringResource(R.string.sheet_class), it) },
        speciesFact(sheet.header.species, size)?.let { GlanceFact(Icons.Outlined.Person, stringResource(R.string.sheet_species), it) },
        identity.background?.let { GlanceFact(Icons.Outlined.AutoStories, stringResource(R.string.sheet_background), it) },
        identity.languages.joinToString(", ").ifEmpty { null }?.let { GlanceFact(Icons.Outlined.Translate, stringResource(R.string.sheet_languages), it) },
    )
    if (facts.isNotEmpty()) GlanceCard { GlanceFacts(facts) }
}
