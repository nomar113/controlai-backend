package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.AppleLoginFlowModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

interface AppleLoginFlowRepository : JpaRepository<AppleLoginFlowModel, Long> {
    fun findByNonceDigest(nonceDigest: String): AppleLoginFlowModel?

    fun findByHandoffCodeHash(handoffCodeHash: String): AppleLoginFlowModel?

    // Single use enforced in SQL: the nonce is consumed and the handoff code saved in one step, and only
    // while the nonce is still unused. A replayed callback updates zero rows.
    @Modifying
    @Transactional
    @Query(
        "UPDATE AppleLoginFlowModel m SET m.consumedAt = :now, m.handoffCodeHash = :handoffCodeHash, " +
            "m.userId = :userId, m.handoffExpiresAt = :handoffExpiresAt " +
            "WHERE m.id = :id AND m.consumedAt IS NULL",
    )
    fun saveHandoffIfNonceUnused(
        @Param("id") id: Long,
        @Param("now") now: Instant,
        @Param("handoffCodeHash") handoffCodeHash: String,
        @Param("userId") userId: Long,
        @Param("handoffExpiresAt") handoffExpiresAt: Instant,
    ): Int

    @Modifying
    @Transactional
    @Query("UPDATE AppleLoginFlowModel m SET m.handoffConsumedAt = :now WHERE m.id = :id AND m.handoffConsumedAt IS NULL")
    fun consumeHandoffIfUnused(@Param("id") id: Long, @Param("now") now: Instant): Int
}
