package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface UserRepository : JpaRepository<UserModel, Long> {
    fun findByEmail(email: String): UserModel?
    fun findByGoogleSub(googleSub: String): UserModel?

    // Conditional updates: the affected row count tells whether the state actually changed,
    // so concurrent requests cannot both succeed
    @Modifying
    @Transactional
    @Query(
        "UPDATE UserModel u SET u.deletionRequestedAt = :requestedAt, u.deletionScheduledFor = :scheduledFor " +
            "WHERE u.id = :userId AND u.deletionScheduledFor IS NULL",
    )
    fun scheduleDeletion(
        @Param("userId") userId: Long,
        @Param("requestedAt") requestedAt: Instant,
        @Param("scheduledFor") scheduledFor: Instant,
    ): Int

    @Modifying
    @Transactional
    @Query(
        "UPDATE UserModel u SET u.deletionRequestedAt = NULL, u.deletionScheduledFor = NULL " +
            "WHERE u.id = :userId AND u.deletionScheduledFor IS NOT NULL",
    )
    fun clearDeletion(@Param("userId") userId: Long): Int
}
