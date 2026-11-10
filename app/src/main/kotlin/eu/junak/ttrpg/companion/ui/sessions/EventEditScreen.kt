package eu.junak.ttrpg.companion.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.CampaignDataset
import eu.junak.ttrpg.companion.core.model.CampaignPage
import eu.junak.ttrpg.companion.core.model.EventFields
import eu.junak.ttrpg.companion.core.model.RecordSummary
import eu.junak.ttrpg.companion.core.model.normalizeIdentity
import eu.junak.ttrpg.companion.core.model.settings
import eu.junak.ttrpg.companion.core.model.summaries
import eu.junak.ttrpg.companion.ui.components.CenteredMessage
import eu.junak.ttrpg.companion.ui.components.Portrait
import eu.junak.ttrpg.companion.ui.components.SectionHeader
import eu.junak.ttrpg.companion.ui.components.StatusDot
import eu.junak.ttrpg.companion.ui.components.pageLabel
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.optionColor

/**
 * Creates or edits one event: name, session, date, priority, summary,
 * Markdown description, the characters and places involved and, for the DM,
 * whether only the DM sees it. Saving merges with changes made elsewhere;
 * leaving with unsaved changes asks first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(onClose: () -> Unit, viewModel: EventEditorViewModel = hiltViewModel()) {
    val spacing = LocalSpacing.current
    val status by viewModel.status.collectAsStateWithLifecycle()
    val draft by viewModel.fields.collectAsStateWithLifecycle()
    val campaign by viewModel.campaign.collectAsStateWithLifecycle()
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val leave = { if (viewModel.dirty) confirmLeave = true else onClose() }
    LaunchedEffect(status) { if (status == EditorStatus.Done) onClose() }
    BackHandler(enabled = status != EditorStatus.Done, onBack = leave)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (viewModel.creating) R.string.event_new else R.string.event_edit)) },
                navigationIcon = {
                    IconButton(onClick = leave) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_close)) }
                },
                actions = {
                    if (!viewModel.creating && draft != null) {
                        IconButton(onClick = { confirmDelete = true }, enabled = status != EditorStatus.Saving) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                    if (status == EditorStatus.Saving) {
                        CircularProgressIndicator(Modifier.padding(horizontal = spacing.md).size(spacing.lg))
                    } else {
                        TextButton(onClick = { viewModel.save() }, enabled = draft != null) { Text(stringResource(R.string.action_save)) }
                    }
                },
            )
        },
    ) { padding ->
        val fields = draft
        val dataset = campaign.dataset
        if (fields == null || dataset == null) {
            Column(Modifier.padding(padding)) {
                val problem = (status as? EditorStatus.Problem)?.problem
                CenteredMessage(if (problem != null) saveProblemText(problem) else stringResource(R.string.campaign_loading), loading = problem == null)
            }
            return@Scaffold
        }
        Column(
            Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(spacing.md),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            (status as? EditorStatus.Problem)?.problem?.takeUnless { it is SaveProblem.Conflict }?.let {
                Text(saveProblemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            EventForm(fields, dataset, viewModel.site?.baseUrl.orEmpty(), viewModel.canManageVisibility, viewModel::update)
        }
    }

    (status as? EditorStatus.Problem)?.problem?.let { problem ->
        if (problem is SaveProblem.Conflict) {
            AlertDialog(
                onDismissRequest = viewModel::dismissProblem,
                title = { Text(stringResource(R.string.save_conflict_title)) },
                text = { Text(stringResource(R.string.save_conflict_text, problem.fields.map { storedFieldLabel(it) }.joinToString(", "))) },
                confirmButton = { TextButton(onClick = { viewModel.save(overwrite = true) }) { Text(stringResource(R.string.action_overwrite)) } },
                dismissButton = { TextButton(onClick = viewModel::dismissProblem) { Text(stringResource(R.string.action_keep_editing)) } },
            )
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_text)) },
            confirmButton = { TextButton(onClick = { confirmLeave = false; onClose() }) { Text(stringResource(R.string.action_discard)) } },
            dismissButton = { TextButton(onClick = { confirmLeave = false }) { Text(stringResource(R.string.action_keep_editing)) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.event_delete_title, draft?.name.orEmpty())) },
            text = { Text(stringResource(R.string.event_delete_text)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete() }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EventForm(
    fields: EventFields,
    dataset: CampaignDataset,
    baseUrl: String,
    canManageVisibility: Boolean,
    onChange: (EventFields) -> Unit,
) {
    val spacing = LocalSpacing.current
    val words = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
    OutlinedTextField(
        value = fields.name,
        onValueChange = { onChange(fields.copy(name = it.replace('\n', ' '))) },
        label = { Text(stringResource(R.string.field_name)) },
        singleLine = true,
        keyboardOptions = words,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        var session by rememberSaveable { mutableStateOf(fields.sitting.toString()) }
        OutlinedTextField(
            value = session,
            onValueChange = { text ->
                session = text.filter(Char::isDigit).take(7)
                onChange(fields.copy(sitting = session.toIntOrNull() ?: 0))
            },
            label = { Text(stringResource(R.string.field_session)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(0.4f),
        )
        OutlinedTextField(
            value = fields.date,
            onValueChange = { onChange(fields.copy(date = it.replace('\n', ' '))) },
            label = { Text(stringResource(R.string.field_date)) },
            singleLine = true,
            modifier = Modifier.weight(0.6f),
        )
    }
    PriorityField(fields, dataset, onChange)
    OutlinedTextField(
        value = fields.short,
        onValueChange = { onChange(fields.copy(short = it)) },
        label = { Text(stringResource(R.string.field_short_summary)) },
        maxLines = 3,
        keyboardOptions = words,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = fields.description,
        onValueChange = { onChange(fields.copy(description = it)) },
        label = { Text(stringResource(R.string.field_description_markdown)) },
        minLines = 6,
        keyboardOptions = words,
        modifier = Modifier.fillMaxWidth(),
    )
    ReferenceField(CampaignPage.CHARACTERS, fields.characters, dataset, baseUrl) { onChange(fields.copy(characters = it)) }
    ReferenceField(CampaignPage.LOCATIONS, fields.locations, dataset, baseUrl) { onChange(fields.copy(locations = it)) }
    if (canManageVisibility) {
        Row(
            Modifier.fillMaxWidth().clickable { onChange(fields.copy(dmOnly = !fields.dmOnly)) }.padding(vertical = spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.event_dm_only), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.event_dm_only_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = fields.dmOnly, onCheckedChange = { onChange(fields.copy(dmOnly = it)) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriorityField(fields: EventFields, dataset: CampaignDataset, onChange: (EventFields) -> Unit) {
    val priorities = remember(dataset) { dataset.settings.eventPriorities }
    if (priorities.isEmpty() && fields.priority.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val selected = priorities.firstOrNull { it.id == fields.priority }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = selected?.label ?: fields.priority.ifEmpty { stringResource(R.string.priority_none) },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.fact_priority)) },
            leadingIcon = selected?.color?.let { color -> { StatusDot(optionColor(color, Color.Unspecified)) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.priority_none)) }, onClick = { open = false; onChange(fields.copy(priority = "")) })
            priorities.forEach { priority ->
                DropdownMenuItem(
                    text = { Text(priority.label) },
                    leadingIcon = { StatusDot(optionColor(priority.color, MaterialTheme.colorScheme.primary)) },
                    onClick = { open = false; onChange(fields.copy(priority = priority.id)) },
                )
            }
        }
    }
}

/**
 * The records of [page] an event names, as removable chips with a picker.
 * Keys the session cannot see stay in the record (the host keeps them) but
 * are not shown.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ReferenceField(
    page: CampaignPage,
    keys: List<String>,
    dataset: CampaignDataset,
    baseUrl: String,
    onChange: (List<String>) -> Unit,
) {
    val spacing = LocalSpacing.current
    val options = remember(dataset, page) { dataset.summaries(page) }
    var picking by rememberSaveable { mutableStateOf(false) }
    SectionHeader(pageLabel(page))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
        keys.mapNotNull { key -> options.firstOrNull { it.key == key } }.forEach { summary ->
            InputChip(
                selected = false,
                onClick = { onChange(keys - summary.key) },
                label = { Text(summary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                avatar = { Portrait(baseUrl, summary.portrait, summary.name, page, spacing.md + spacing.xs, summary.icon) },
                trailingIcon = { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.action_remove_item, summary.name), Modifier.size(spacing.md)) },
            )
        }
        if (options.isNotEmpty()) {
            AssistChip(
                onClick = { picking = true },
                label = { Text(stringResource(R.string.action_choose)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, Modifier.size(spacing.md + spacing.xxs)) },
            )
        }
    }
    if (picking) {
        RecordPicker(page, options, keys.toSet(), baseUrl, onDismiss = { picking = false }) { chosen ->
            // Keep the stored order, hidden keys included, and add new choices at the end.
            onChange(keys.filter { it in chosen || options.none { option -> option.key == it } } + chosen.filter { it !in keys })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPicker(
    page: CampaignPage,
    options: List<RecordSummary>,
    selected: Set<String>,
    baseUrl: String,
    onDismiss: () -> Unit,
    onChange: (List<String>) -> Unit,
) {
    val spacing = LocalSpacing.current
    var query by rememberSaveable { mutableStateOf("") }
    var chosen by remember { mutableStateOf(options.map { it.key }.filter { it in selected }) }
    val shown = remember(options, query) {
        val needle = normalizeIdentity(query)
        options.filter { needle.isEmpty() || needle in normalizeIdentity(it.name) }
    }
    ModalBottomSheet(onDismissRequest = { onChange(chosen); onDismiss() }) {
        Column(Modifier.padding(horizontal = spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(pageLabel(page), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(chosen); onDismiss() }) { Text(stringResource(R.string.action_done)) }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(shown, key = { it.key }) { summary ->
                val checked = summary.key in chosen
                val toggle = { chosen = if (checked) chosen - summary.key else chosen + summary.key }
                ListItem(
                    headlineContent = { Text(summary.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = summary.subtitle.takeIf(String::isNotEmpty)?.let { { Text(it, maxLines = 1) } },
                    leadingContent = { Portrait(baseUrl, summary.portrait, summary.name, page, spacing.portraitSmall, summary.icon) },
                    trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.clickable(onClick = toggle),
                )
            }
        }
    }
}
