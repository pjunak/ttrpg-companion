package eu.junak.ttrpg.companion.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.AppPreferences
import eu.junak.ttrpg.companion.BuildConfig
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.feature.update.UpdateState
import eu.junak.ttrpg.companion.feature.update.Updater
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import eu.junak.ttrpg.companion.ui.theme.ThemeMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(val updater: Updater, val preferences: AppPreferences) : ViewModel()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val spacing = LocalSpacing.current
    val updater = viewModel.updater
    val update by updater.state.collectAsStateWithLifecycle()
    val themeMode by viewModel.preferences.themeMode.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }, navigationIcon = { BackButton(onBack) }) },
    ) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(spacing.md).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.sm),
        ) {
            Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.appearance_text), color = MaterialTheme.colorScheme.onSurfaceVariant)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { viewModel.preferences.setThemeMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size),
                    ) {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.appearance_system
                                    ThemeMode.LIGHT -> R.string.appearance_light
                                    ThemeMode.DARK -> R.string.appearance_dark
                                },
                            ),
                        )
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
            Text(stringResource(R.string.updates_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (val state = update) {
                UpdateState.Idle -> OutlinedButton(onClick = updater::check) { Text(stringResource(R.string.update_check)) }
                UpdateState.Checking -> Text(stringResource(R.string.update_checking))
                UpdateState.UpToDate -> {
                    Text(stringResource(R.string.update_up_to_date))
                    OutlinedButton(onClick = updater::check) { Text(stringResource(R.string.update_check)) }
                }
                is UpdateState.Available -> {
                    Text(stringResource(R.string.update_available, state.version))
                    state.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                        Button(onClick = { updater.download(state) }) { Text(stringResource(R.string.update_download)) }
                        TextButton(onClick = updater::dismiss) { Text(stringResource(R.string.update_dismiss)) }
                    }
                }
                is UpdateState.Downloading -> {
                    Text(stringResource(R.string.update_downloading, (state.progress * 100).toInt()))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
                }
                is UpdateState.ReadyToInstall -> Button(onClick = { updater.install(state.file) }) {
                    Text(stringResource(R.string.update_install))
                }
                is UpdateState.Error -> {
                    Text(state.message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = updater::check) { Text(stringResource(R.string.update_check)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = spacing.sm))
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.about_fonts), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
