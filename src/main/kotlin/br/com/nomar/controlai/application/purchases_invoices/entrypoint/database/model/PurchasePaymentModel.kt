package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal

@Entity
@Table(name = "purchase_payments")
data class PurchasePaymentModel(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "type", nullable = false)
    val type: String,

    @Column(name = "value", precision = 10, scale = 2, nullable = false)
    val value: BigDecimal,

    @Column(name = "purchase_invoices_id", nullable = false)
    val purchaseInvoiceId: Long,
)
