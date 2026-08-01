package br.com.nomar.controlai.application.auth.entrypoint.rest.response

import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.Profile
import br.com.nomar.controlai.domain.auth.entity.User

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
