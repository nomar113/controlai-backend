package br.com.nomar.controlai.application.payments_notification.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payments_notifications")
data class PaymentNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "card_last_digits", length = 4, nullable = false)
    val cardLastDigits: String,

    @Column(name = "purchased_at", nullable = false)
    val purchasedAt: LocalDateTime,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "merchant_name", nullable = false)
    val merchantName: String,

    @Column(name = "number_of_installments", nullable = false)
    val numberOfInstallments: Int = 1,

    @Column(name = "origin", nullable = false)
    val origin: String = "HTTP_REQUEST",

    @Column(name = "origin_type", nullable = false)
    val originType: String = "HTTP_REQUEST",

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null,

)
