package eu.junak.ttrpg.companion.ui.sites

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import eu.junak.ttrpg.companion.BuildConfig
import eu.junak.ttrpg.companion.R
import eu.junak.ttrpg.companion.core.model.SiteAddressResult
import eu.junak.ttrpg.companion.core.network.site.Site
import eu.junak.ttrpg.companion.core.network.site.SiteException
import eu.junak.ttrpg.companion.core.network.site.SiteProblem
import eu.junak.ttrpg.companion.core.network.site.SiteRepository
import eu.junak.ttrpg.companion.core.network.site.SiteStore
import eu.junak.ttrpg.companion.navigation.SITE_ID_ARGUMENT
import eu.junak.ttrpg.companion.ui.components.BackButton
import eu.junak.ttrpg.companion.ui.theme.LocalSpacing
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddSiteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    store: SiteStore,
    private val repository: SiteRepository,
) : ViewModel() {

    /** Set when signing in to a saved site again; its address is fixed. */
    val existing: Site? = savedStateHandle.get<String>(SITE_ID_ARGUMENT)?.let(store::site)

    var address by mutableStateOf(existing?.baseUrl.orEmpty())
    var password by mutableStateOf("")
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<SiteProblem?>(null)
        private set
    var done by mutableStateOf<Site?>(null)
        private set

    fun submit() {
        if (busy) return
        busy = true
        problem = null
        viewModelScope.launch {
            try {
                done = existing?.let { repository.signIn(it, password) }
                    ?: repository.add(address, password, allowLocalHttp = BuildConfig.DEBUG)
            } catch (error: SiteException) {
                problem = error.problem
            } finally {
                busy = false
                password = ""
            }
        }
    }
}

/** Adds a campaign site, or signs in to a saved one again. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSiteScreen(onBack: () -> Unit, onDone: (Site) -> Unit, viewModel: AddSiteViewModel = hiltViewModel()) {
    val spacing = LocalSpacing.current
    val existing = viewModel.existing
    LaunchedEffect(viewModel.done) { viewModel.done?.let(onDone) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (existing == null) stringResource(R.string.add_site_title)
                        else stringResource(R.string.sign_in_title, existing.name),
                    )
                },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(spacing.md).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            OutlinedTextField(
                value = viewModel.address,
                onValueChange = { viewModel.address = it },
                label = { Text(stringResource(R.string.field_address)) },
                placeholder = { Text("asurai.junak.eu") },
                singleLine = true,
                enabled = existing == null && !viewModel.busy,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                label = { Text(stringResource(R.string.field_password)) },
                supportingText = { Text(stringResource(R.string.password_hint)) },
                singleLine = true,
                enabled = !viewModel.busy,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            viewModel.problem?.let { Text(problemText(it), color = MaterialTheme.colorScheme.error) }
            Button(onClick = viewModel::submit, enabled = !viewModel.busy, modifier = Modifier.fillMaxWidth()) {
                if (viewModel.busy) {
                    CircularProgressIndicator(strokeWidth = spacing.xs / 2, modifier = Modifier.padding(end = spacing.sm))
                }
                Text(stringResource(if (existing == null) R.string.action_add else R.string.action_sign_in))
            }
        }
    }
}

@Composable
private fun problemText(problem: SiteProblem): String = when (problem) {
    is SiteProblem.Address -> stringResource(
        when (problem.reason) {
            SiteAddressResult.Reason.EMPTY -> R.string.problem_address_empty
            SiteAddressResult.Reason.MALFORMED -> R.string.problem_address_malformed
            SiteAddressResult.Reason.HTTPS_REQUIRED -> R.string.problem_https
        },
    )
    is SiteProblem.UnsupportedHost -> stringResource(R.string.problem_unsupported, problem.version)
    SiteProblem.WrongPassword -> stringResource(R.string.problem_wrong_password)
    SiteProblem.TooManyAttempts -> stringResource(R.string.problem_too_many)
    is SiteProblem.Unreachable -> stringResource(R.string.problem_unreachable, problem.detail)
}
