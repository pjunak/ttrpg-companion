package eu.junak.ttrpg.companion.core.model

/**
 * The TTRPG Codex host versions this app understands (`GET /api/version`). The
 * routes it uses are listed in the host's client API reference; a new major
 * version may change them.
 */
object HostVersion {
    const val SUPPORTED_MAJOR = 2

    /** True for `2.x.y`, with or without a pre-release or build suffix. */
    fun isSupported(version: String): Boolean {
        val match = Regex("^(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+].*)?$").matchEntire(version.trim()) ?: return false
        return match.groupValues[1].toIntOrNull() == SUPPORTED_MAJOR
    }
}
