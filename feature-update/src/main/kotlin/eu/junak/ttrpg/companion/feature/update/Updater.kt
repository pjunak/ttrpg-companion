package eu.junak.ttrpg.companion.feature.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val notes: String?, val apk: ReleasedApk) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class ReadyToInstall(val file: File, val version: String) : UpdateState
    data class Error(val message: String) : UpdateState
}

/**
 * Checks this repository's latest GitHub release for a newer build, downloads
 * and verifies its APK and opens Android's installer, which asks the user.
 * Talks only to GitHub, never to a campaign site.
 */
@Singleton
class Updater @Inject constructor(@ApplicationContext private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var launchCheckDone = false

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun check() {
        _state.value = UpdateState.Checking
        scope.launch { _state.value = latest() }
    }

    /** Once per process and silently: publishes only an available update. */
    fun checkOnLaunch() {
        if (launchCheckDone) return
        launchCheckDone = true
        scope.launch {
            val result = latest()
            if (result is UpdateState.Available && _state.value == UpdateState.Idle) _state.value = result
        }
    }

    fun download(update: UpdateState.Available) {
        _state.value = UpdateState.Downloading(0f)
        scope.launch {
            val apk = update.apk
            val file = File(context.cacheDir, "updates/ttrpg-companion-${apk.versionCode}.apk")
            file.parentFile?.listFiles()?.forEach { if (it != file) it.delete() }
            runCatching {
                downloadTo(apk.url, file, apk.size)
                verifyPackage(file, apk.sha256, apk.size)
                val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
                if (info?.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(info) != apk.versionCode) {
                    throw IOException(context.getString(R.string.update_error_package))
                }
            }.onSuccess {
                _state.value = UpdateState.ReadyToInstall(file, update.version)
                install(file)
            }.onFailure {
                file.delete()
                _state.value = UpdateState.Error(context.getString(R.string.update_error_download, it.message))
            }
        }
    }

    /** Opens the installer, or first the setting that lets this app install packages. */
    fun install(file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure {
            _state.value = UpdateState.Error(context.getString(R.string.update_error_installer, it.message))
        }
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }

    private fun latest(): UpdateState {
        val repo = BuildConfig.UPDATE_REPO.takeIf { Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(it) }
            ?: return UpdateState.Error(context.getString(R.string.update_error_source))
        val release = try {
            client.newCall(
                Request.Builder()
                    .url("https://api.github.com/repos/$repo/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .build(),
            ).execute().use { response ->
                // No published release yet is not an error.
                if (response.code == 404) return UpdateState.UpToDate
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                json.decodeFromString(GitHubRelease.serializer(), response.body.string())
            }
        } catch (error: Exception) {
            return UpdateState.Error(
                context.getString(R.string.update_error_check, error.message ?: context.getString(R.string.update_error_network)),
            )
        }
        return runCatching {
            val current = PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))
            val apk = releasedApk(release, repo, current)
            if (apk == null) UpdateState.UpToDate else UpdateState.Available(apk.versionName, release.body, apk)
        }.getOrElse { UpdateState.Error(context.getString(R.string.update_error_package)) }
    }

    private fun downloadTo(url: String, file: File, expectedSize: Long) {
        file.parentFile?.mkdirs()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body.byteStream().use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        done += read
                        if (done > expectedSize) throw IOException(context.getString(R.string.update_error_package))
                        output.write(buffer, 0, read)
                        _state.value = UpdateState.Downloading((done.toFloat() / expectedSize).coerceIn(0f, 1f))
                    }
                }
            }
        }
    }
}
