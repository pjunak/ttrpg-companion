package eu.junak.ttrpg.companion.feature.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** The part of GitHub's latest-release answer the updater reads. */
@Serializable
internal data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
internal data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
    val digest: String? = null,
)

data class ReleasedApk(
    val versionName: String,
    val versionCode: Long,
    val url: String,
    val sha256: String,
    val size: Long,
)

/**
 * The APK a release offers, or null when it is not newer than [currentCode].
 * CI names each release `v<name>` with one asset `ttrpg-companion-<code>.apk`;
 * the increasing build number decides availability, and GitHub's SHA-256
 * digest is required so the download can be checked.
 */
internal fun releasedApk(release: GitHubRelease, repo: String, currentCode: Long): ReleasedApk? {
    val name = Regex("v([0-9]+\\.[0-9]+\\.[0-9]+)").matchEntire(release.tagName)?.groupValues?.get(1)
        ?: error("Unexpected release tag")
    val asset = release.assets.single { it.name.endsWith(".apk") }
    val code = Regex("ttrpg-companion-([0-9]+)\\.apk").matchEntire(asset.name)?.groupValues?.get(1)?.toLong()
        ?: error("Unexpected package name")
    require(code in 1..2_100_000_000L && asset.size in 1..MAX_APK_BYTES)
    val digest = asset.digest?.takeIf { Regex("sha256:[0-9a-f]{64}").matches(it) } ?: error("Missing package digest")
    val url = URI(asset.downloadUrl)
    require(url.scheme == "https" && url.host == "github.com" && url.userInfo == null && url.query == null)
    require(url.path == "/$repo/releases/download/${release.tagName}/${asset.name}")
    if (code <= currentCode) return null
    return ReleasedApk(name, code, asset.downloadUrl, digest.removePrefix("sha256:"), asset.size)
}

internal fun verifyPackage(file: File, expectedSha256: String, expectedSize: Long) {
    require(file.length() == expectedSize) { "Package size mismatch" }
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    val actual = digest.digest().joinToString("") { "%02x".format(it) }
    require(actual == expectedSha256) { "Package checksum mismatch" }
}

internal const val MAX_APK_BYTES = 250L * 1024 * 1024
