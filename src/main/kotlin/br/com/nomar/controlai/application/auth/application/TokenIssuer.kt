package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.auth.entrypoint.database.model.RefreshTokenModel
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.RefreshTokenRepository
import br.com.nomar.controlai.application.groups.entrypoint.database.repository.GroupMemberRepository
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.auth.entity.AuthSession
import br.com.nomar.controlai.domain.auth.entity.User
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.Base64

data class IssuedSession(
    val session: AuthSession,
    val refreshTokenId: Long,
)

@Component
class TokenIssuer(
    private val jwtEncoder: JwtEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val groupMemberRepository: GroupMemberRepository,
) {

    private val secureRandom = SecureRandom()

    // When rotating, absoluteExpiresAt carries over from the original token (90-day hard cap)
    fun issueSession(user: User, absoluteExpiresAt: Instant? = null): IssuedSession {
        val userId = requireNotNull(user.id) { "User must be persisted before issuing tokens" }
        val groupId = groupMemberRepository.findByUserId(userId)?.groupId
            ?: throw IllegalStateException("User $userId has no group membership")

        val now = Instant.now()
        val claims = JwtClaimsSet.builder()
            .subject(userId.toString())
            .issuedAt(now)
            .expiresAt(now.plus(ACCESS_TOKEN_TTL))
            .claim("groupId", groupId)
            .claim("email", user.email)
            .build()
        val header = JwsHeader.with(MacAlgorithm.HS256).build()
        val accessToken = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).tokenValue

        val rawRefreshToken = generateOpaqueToken()
        val absolute = absoluteExpiresAt ?: now.plus(ABSOLUTE_REFRESH_TTL)
        val saved = refreshTokenRepository.save(
            RefreshTokenModel(
                userId = userId,
                tokenHash = TokenHasher.sha256(rawRefreshToken),
                expiresAt = minOf(now.plus(REFRESH_TOKEN_TTL), absolute),
                absoluteExpiresAt = absolute,
            ),
        )

        return IssuedSession(
            session = AuthSession(accessToken = accessToken, refreshToken = rawRefreshToken, user = user),
            refreshTokenId = saved.id!!,
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(REFRESH_TOKEN_BYTES)
        secureRandom.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        val ACCESS_TOKEN_TTL: Duration = Duration.ofMinutes(15)
        val REFRESH_TOKEN_TTL: Duration = Duration.ofDays(30)
        val ABSOLUTE_REFRESH_TTL: Duration = Duration.ofDays(90)
        private const val REFRESH_TOKEN_BYTES = 32
    }
}
