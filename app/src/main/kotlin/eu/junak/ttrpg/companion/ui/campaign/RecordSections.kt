package eu.junak.ttrpg.companion.ui.campaign

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.m3.Markdown
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.CampaignMarkdown
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.Fact
import eu.junak.ttrpg.companion.core.model.FactKind
import eu.junak.ttrpg.companion.core.model.Question
import eu.junak.ttrpg.companion.core.model.RecordDetails
import eu.junak.ttrpg.companion.core.model.RecordRef
import eu.junak.ttrpg.companion.core.model.details
import eu.junak.ttrpg.companion.core.model.text
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.components.factLabel
import eu.junak.ttrpg.companion.ui.components.fieldLabel
import eu.junak.ttrpg.companion.ui.components.partyName
import eu.junak.ttrpg.companion.ui.theme.ArticleTheme
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/** One labelled fact; linked values open their record. */
@Composable
internal fun FactRow(fact: Fact, onOpen: (RecordRef) -> Unit) {
    val spacing = LocalSpacing.current
    val link = fact.link
    var modifier = Modifier.fillMaxWidth()
    if (link != null) modifier = modifier.clickable { onOpen(link) }
    Row(modifier.padding(horizontal = spacing.md, vertical = spacing.sm + spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Text(
            factLabel(fact.kind),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.4f),
        )
        Row(Modifier.weight(0.6f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
            if (fact.color != null) StatusDot(optionColor(fact.color, MaterialTheme.colorScheme.primary))
            val flag = fact.flag
            val text = when {
                flag != null -> stringResource(if (flag) R.string.fact_yes else R.string.fact_no)
                fact.kind == FactKind.FACTION || fact.kind == FactKind.OWNER -> partyName(fact.text)
                else -> fact.text
            }
            Text(
                listOf(fact.icon.takeIf { fact.color == null }.orEmpty(), text).filter(String::isNotEmpty).joinToString(" "),
                style = MaterialTheme.typography.bodyLarge,
                color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (link != null) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(spacing.md),
                )
            }
        }
    }
}

/** The record's Markdown fields; the first has no heading, the rest are named. */
@Composable
internal fun Article(page: CampaignPage, details: RecordDetails, dataset: CampaignDataset, baseUrl: String) {
    val record = dataset.record(page.collection, details.summary.key) ?: return
    val fields = page.articleFields.filter { record.value.text(it).isNotEmpty() }
    // An event's short note is its subtitle when it has no date, so it is not repeated.
    val shown = if (details.summary.subtitle == record.value.text("short")) fields - "short" else fields
    ArticleTheme {
        shown.forEachIndexed { index, field ->
            if (index > 0) SectionHeader(fieldLabel(field)) else Spacer(Modifier.size(LocalSpacing.current.md))
            val markdown = record.value.text(field)
            Markdown(
                content = remember(markdown, dataset, baseUrl) { CampaignMarkdown.prepare(markdown, dataset, baseUrl) },
                imageTransformer = Coil3ImageTransformerImpl,
            )
        }
    }
}

@Composable
internal fun QuestionRow(question: Question) {
    val spacing = LocalSpacing.current
    val answered = question.answer.isNotEmpty()
    Row(Modifier.padding(vertical = spacing.sm), horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        Icon(
            if (answered) Icons.Filled.CheckCircle else Icons.AutoMirrored.Outlined.HelpOutline,
            contentDescription = stringResource(if (answered) R.string.record_question_answered else R.string.record_question_open),
            tint = if (answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(spacing.iconMedium),
        )
        ArticleTheme {
            Column(verticalArrangement = Arrangement.spacedBy(spacing.xxs)) {
                Text(question.text, style = MaterialTheme.typography.bodyLarge)
                if (answered) {
                    Text(question.answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Tags(tags: List<String>) {
    val spacing = LocalSpacing.current
    SectionHeader(stringResource(R.string.record_tags))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm), verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        tags.forEach { tag ->
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(tag, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = spacing.sm, vertical = spacing.xs))
            }
        }
    }
}
