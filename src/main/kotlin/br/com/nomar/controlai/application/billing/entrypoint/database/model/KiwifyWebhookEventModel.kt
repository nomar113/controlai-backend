package br.com.nomar.controlai.application.billing.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

@Entity
@Table(name = "kiwify_webhook_events")
data class KiwifyWebhookEventModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "kiwify_event_id", nullable = false, unique = true)
    val kiwifyEventId: String = "",

    @Column(name = "order_status", nullable = false)
    val orderStatus: String = "",

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "json")
    val rawPayload: String = "{}",

    @Column(name = "processed_at")
    val processedAt: Instant? = null,
)
