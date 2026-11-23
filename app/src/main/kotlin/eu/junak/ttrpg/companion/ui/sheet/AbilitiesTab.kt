package eu.junak.ttrpg.companion.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.AbilityRow
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.Proficiency
import eu.junak.ttrpg.companion.core.model.character.SheetAbilities
import eu.junak.ttrpg.companion.core.model.character.SkillRow
import eu.junak.ttrpg.companion.ui.components.GlanceCard
import eu.junak.ttrpg.companion.ui.components.GlanceTiles
import eu.junak.ttrpg.companion.ui.components.GlanceValue
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.Tag
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.NumberStyles
import eu.junak.ttrpg.companion.ui.theme.TitleFont

/**
 * The Abilities tab: its glance card (proficiency, Passive Perception and any
 * special senses), the ability cards with their saves (a filled shield marks
 * a proficient save), then the skills. It only reads the sheet.
 */
@Composable
fun AbilitiesTab(sheet: CharacterSheet) {
    val spacing = LocalSpacing.current
    val abilities = sheet.abilities
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, bottom = spacing.lg)) {
        item(key = "glance") { AbilitiesGlance(abilities) }
        item {
            Column(Modifier.padding(top = spacing.md - spacing.xs), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                abilities.abilities.chunked(TILES_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        row.forEach { AbilityTile(it, Modifier.weight(1f)) }
                        repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (abilities.skills.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.sheet_skills)) }
            item {
                val (left, right) = skillColumns(abilities.skills)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.md)) {
                    Column(Modifier.weight(1f)) { left.forEach { SkillLine(it) } }
                    Column(Modifier.weight(1f)) { right.forEach { SkillLine(it) } }
                }
            }
        }
    }
}

private const val TILES_PER_ROW = 3

@Composable
private fun AbilitiesGlance(abilities: SheetAbilities) {
    val proficiency = signed(abilities.proficiencyBonus)
    val values = listOf(
        GlanceValue(
            Icons.Outlined.WorkspacePremium,
            proficiency,
            stringResource(R.string.sheet_proficiency),
            stringResource(R.string.sheet_proficiency_description, proficiency),
        ),
        GlanceValue(
            Icons.Outlined.Visibility,
            abilities.passivePerception.toString(),
            stringResource(R.string.sheet_passive_perception),
            stringResource(R.string.sheet_passive_perception_description, abilities.passivePerception),
        ),
    ) + abilities.senses.map { sense ->
        val name = senseName(sense.id)?.let { stringResource(it) } ?: readableId(sense.id)
        GlanceValue(Icons.Outlined.DarkMode, sense.range.toString(), name, stringResource(R.string.sheet_sense_description, name, sense.range))
    }
    GlanceCard { GlanceTiles(values, TILES_PER_ROW) }
}

@Composable
private fun AbilityTile(row: AbilityRow, modifier: Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val name = row.ability?.let { stringResource(abilityName(it)) } ?: row.id
    val save = signed(row.save)
    val described = stringResource(R.string.sheet_ability_description, name, signed(row.modifier), row.score.toString(), save)
    val description = if (row.saveProficient) stringResource(R.string.sheet_ability_save_proficient, described) else described
    Surface(
        color = colors.surfaceContainer,
        border = BorderStroke(spacing.hairline, colors.outlineVariant),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
    ) {
        Column(Modifier.padding(vertical = spacing.xs + spacing.xxs, horizontal = spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(row.id, style = MaterialTheme.typography.labelLarge.copy(fontFamily = TitleFont), color = colors.primary)
            Text(signed(row.modifier), style = NumberStyles.Large)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(row.score.toString(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (row.saveProficient) Icons.Filled.Shield else Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = if (row.saveProficient) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.size(spacing.iconInline),
                    )
                    Text(
                        save,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = if (row.saveProficient) FontWeight.SemiBold else FontWeight.Normal),
                        color = if (row.saveProficient) colors.primary else colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillLine(skill: SkillRow) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val trained = skill.proficiency != Proficiency.NONE
    val name = skill.skill?.let { stringResource(skillName(it)) } ?: skill.id
    val training = stringResource(proficiencyName(skill.proficiency))
    val disadvantage = stringResource(R.string.sheet_disadvantage)
    val description = listOfNotNull(name, training, signed(skill.bonus), disadvantage.takeIf { skill.disadvantage }).joinToString(", ")
    Column(Modifier.clearAndSetSemantics { contentDescription = description }) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TrainingMark(skill.proficiency)
            // The name takes the free space, with its tag right after it; the bonus keeps to the edge.
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (trained) colors.onSurface else colors.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (skill.disadvantage) Tag(stringResource(R.string.sheet_disadvantage_tag))
            }
            Text(
                signed(skill.bonus),
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (trained) FontWeight.SemiBold else FontWeight.Medium),
                color = if (trained) colors.primary else colors.onSurface,
            )
        }
        HorizontalDivider(color = colors.outlineVariant)
    }
}

/** An empty ring for an untrained skill, a filled dot for proficiency and a diamond for expertise. */
@Composable
private fun TrainingMark(proficiency: Proficiency) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    when (proficiency) {
        Proficiency.NONE -> Box(Modifier.size(spacing.sm).border(spacing.hairline, colors.outline, CircleShape))
        Proficiency.PROFICIENT -> Box(Modifier.size(spacing.sm).background(colors.primary, CircleShape))
        Proficiency.EXPERTISE -> Box(Modifier.size(spacing.sm).rotate(45f).background(colors.primary))
    }
}
