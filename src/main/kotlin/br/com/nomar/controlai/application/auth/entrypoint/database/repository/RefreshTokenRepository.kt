package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.RefreshTokenModel
import jakarta.transaction.Transactional
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface RefreshTokenRepository : JpaRepository<RefreshTokenModel, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenModel?

    @Modifying
    @Transactional
    @Query("UPDATE RefreshTokenModel r SET r.revokedAt = :revokedAt WHERE r.userId = :userId AND r.revokedAt IS NULL")
    fun revokeAllActiveByUserId(@Param("userId") userId: Long, @Param("revokedAt") revokedAt: Instant)
}
