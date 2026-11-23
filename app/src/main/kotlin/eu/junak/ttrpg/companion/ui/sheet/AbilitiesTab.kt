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
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.character.AbilityRow
import eu.junak.ttrpg.companion.core.model.character.CharacterSheet
import eu.junak.ttrpg.companion.core.model.character.Proficiency
import eu.junak.ttrpg.companion.core.model.character.SkillRow
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.Tag
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.NumberStyles
import eu.junak.ttrpg.companion.ui.theme.TitleFont

/**
 * The Abilities tab: the ability tiles with their saves (proficient saves in
 * the accent colour), Passive Perception and proficiency, then the skills.
 * It only reads the sheet.
 */
@Composable
fun AbilitiesTab(sheet: CharacterSheet) {
    val spacing = LocalSpacing.current
    val abilities = sheet.abilities
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = spacing.md, end = spacing.md, top = spacing.md - spacing.xs, bottom = spacing.lg)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
                abilities.abilities.chunked(TILES_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        row.forEach { AbilityTile(it, Modifier.weight(1f)) }
                        repeat(TILES_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        item { SummaryLine(abilities.passivePerception, abilities.proficiencyBonus) }
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
private fun AbilityTile(row: AbilityRow, modifier: Modifier) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val name = row.ability?.let { stringResource(abilityName(it)) } ?: row.id
    val save = signed(row.save)
    val saveText = stringResource(R.string.sheet_save, save)
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
            Text(
                buildAnnotatedString {
                    append(row.score.toString() + " · ")
                    val at = saveText.lastIndexOf(save)
                    if (row.saveProficient && at >= 0) {
                        append(saveText.substring(0, at))
                        withStyle(SpanStyle(color = colors.primary, fontWeight = FontWeight.SemiBold)) { append(save) }
                        append(saveText.substring(at + save.length))
                    } else {
                        append(saveText)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SummaryLine(passivePerception: Int, proficiencyBonus: Int) {
    val colors = MaterialTheme.colorScheme
    val strong = SpanStyle(color = colors.onSurface, fontWeight = FontWeight.Medium)
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.sheet_passive_perception) + " ")
            withStyle(strong) { append(passivePerception.toString()) }
            append(" · " + stringResource(R.string.sheet_proficiency) + " ")
            withStyle(strong) { append(signed(proficiencyBonus)) }
            append(" · " + stringResource(R.string.sheet_saves_hint))
        },
        style = MaterialTheme.typography.bodySmall,
        color = colors.onSurfaceVariant,
        modifier = Modifier.padding(vertical = LocalSpacing.current.sm + LocalSpacing.current.xxs),
    )
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
