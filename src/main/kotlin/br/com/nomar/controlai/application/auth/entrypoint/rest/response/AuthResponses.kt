package br.com.nomar.controlai.application.auth.entrypoint.rest.response

import br.com.nomar.controlai.domain.auth.entity.ApiKey
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.Profile
import br.com.nomar.controlai.domain.auth.entity.User
import java.time.Instant

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id!!,
            name = user.name,
            email = user.email,
        )
    }
}

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val user: UserResponse,
) {
    companion object {
        fun from(session: AuthSession) = AuthResponse(
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            user = UserResponse.from(session.user),
        )
    }
}

data class GroupSummaryResponse(
    val id: Long,
    val name: String,
)

data class ProfileResponse(
    val user: UserResponse,
    val group: GroupSummaryResponse,
) {
    companion object {
        fun from(profile: Profile) = ProfileResponse(
            user = UserResponse.from(profile.user),
            group = GroupSummaryResponse(id = profile.group.id!!, name = profile.group.name),
        )
    }
}

// Returned only on POST /me/api-key — includes the raw key value (shown once)
data class ApiKeyCreatedResponse(
    val id: Long,
    val label: String,
    val key: String,
    val createdAt: Instant?,
)

// Returned by GET /me/api-key — raw key is never returned after creation
data class ApiKeyResponse(
    val id: Long,
    val label: String,
    val createdAt: Instant?,
    val revoked: Boolean,
) {
    companion object {
        fun from(apiKey: ApiKey) = ApiKeyResponse(
            id = apiKey.id!!,
            label = apiKey.label,
            createdAt = apiKey.createdAt,
            revoked = apiKey.isRevoked(),
        )
    }
}
