package eu.junak.ttrpg.companion.feature.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ReleaseTest {

    private val repo = "pjunak/ttrpg-companion"
    private val digest = "sha256:" + "a".repeat(64)

    private fun release(code: Long = 12, url: String? = null, sha: String? = digest) = GitHubRelease(
        tagName = "v0.1.$code",
        body = "notes",
        assets = listOf(
            GitHubAsset(
                name = "ttrpg-companion-$code.apk",
                downloadUrl = url ?: "https://github.com/$repo/releases/download/v0.1.$code/ttrpg-companion-$code.apk",
                size = 1000,
                digest = sha,
            ),
        ),
    )

    @Test
    fun offersOnlyANewerBuild() {
        assertEquals(12L, releasedApk(release(), repo, currentCode = 11)?.versionCode)
        assertEquals("a".repeat(64), releasedApk(release(), repo, currentCode = 11)?.sha256)
        assertNull(releasedApk(release(), repo, currentCode = 12))
    }

    @Test
    fun refusesUnverifiablePackages() {
        assertThrows(IllegalStateException::class.java) { releasedApk(release(sha = null), repo, 1) }
        assertThrows(IllegalArgumentException::class.java) {
            releasedApk(release(url = "https://evil.example/ttrpg-companion-12.apk"), repo, 1)
        }
    }
}
