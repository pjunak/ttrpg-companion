package eu.junak.ttrpg.companion.ui.theme

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

/** The languages the app has strings for. */
val AppLanguages = setOf("en", "cs")

/** The device's own language, ignoring any override inside the app. */
fun deviceLanguage(): String = Resources.getSystem().configuration.locales[0].language

/**
 * Shows [content] in [language] (a language code the app has strings for),
 * so a site's screens follow the language its DM chose. Null keeps the
 * device's language.
 */
@Composable
fun ProvideLanguage(language: String?, content: @Composable () -> Unit) {
    val configuration = LocalConfiguration.current
    if (language == null || configuration.locales[0].language == language) {
        content()
        return
    }
    val context = LocalContext.current
    val localized = remember(context, configuration, language) {
        val override = Configuration(configuration).apply { setLocale(Locale.forLanguageTag(language)) }
        LocalizedContext(context, context.createConfigurationContext(override).resources)
    }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        LocalResources provides localized.resources,
        content = content,
    )
}

/** Keeps the activity context (for dialogs, web views and intents) while reading localized resources. */
private class LocalizedContext(base: Context, private val localized: Resources) : ContextWrapper(base) {
    override fun getResources(): Resources = localized
}
