package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.PasswordResetTokenModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface PasswordResetTokenRepository : JpaRepository<PasswordResetTokenModel, Long> {
    fun findByTokenHash(tokenHash: String): PasswordResetTokenModel?

    // Atomic single-use enforcement: updates usedAt only when still null to prevent race conditions.
    @Modifying
    @Transactional
    @Query("UPDATE PasswordResetTokenModel m SET m.usedAt = :usedAt WHERE m.id = :id AND m.usedAt IS NULL")
    fun markUsedByIdIfNotUsed(@Param("id") id: Long, @Param("usedAt") usedAt: Instant): Int
}
