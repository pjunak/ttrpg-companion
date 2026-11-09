package eu.junak.ttrpg.companion.core.model

import kotlinx.serialization.json.JsonElement

enum class Role(val wire: String) { DM("dm"), PLAYER("player") }

/** The session the host reports from `GET /api/auth`, `POST /api/login` and `POST /api/logout`. */
sealed interface AuthState {
    data object Anonymous : AuthState

    data class SignedIn(val role: Role, val csrfToken: String, val expiresAt: String) : AuthState

    companion object {
        private val tokenPattern = Regex("^[A-Za-z0-9_-]{32,512}$")

        fun parse(value: JsonElement, boundary: String = "GET /api/auth"): AuthState {
            val body = value.objectOrNull() ?: throw HostFormatException(boundary, "response must be an object")
            if (body["role"].isNull()) return Anonymous
            if (body["ok"].booleanOrNull() != true) throw HostFormatException(boundary, "ok must be true")
            val role = Role.entries.firstOrNull { it.wire == body["role"].stringOrNull() }
                ?: throw HostFormatException(boundary, "role must be dm or player")
            val csrf = body["csrfToken"].stringOrNull()
            val expires = body["expiresAt"].stringOrNull()
            if (csrf == null || !tokenPattern.matches(csrf) || expires.isNullOrEmpty()) {
                throw HostFormatException(boundary, "session metadata is invalid")
            }
            return SignedIn(role, csrf, expires)
        }
    }
}
