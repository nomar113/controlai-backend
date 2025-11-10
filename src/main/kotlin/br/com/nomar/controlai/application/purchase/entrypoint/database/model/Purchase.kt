package br.com.nomar.controlai.application.purchase.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "purchases")
data class Purchase(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "card_last_digits", length = 4, nullable = false)
    val cardLastDigits: String,

    @Column(name = "purchased_at", nullable = false)
    val purchasedAt: LocalDateTime,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "merchant_name", nullable = false)
    val merchantName: String

)