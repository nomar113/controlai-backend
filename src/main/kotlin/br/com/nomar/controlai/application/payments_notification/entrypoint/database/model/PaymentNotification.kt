package br.com.nomar.controlai.application.payments_notification.entrypoint.database.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLRestriction
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "payment_notifications")
@SQLRestriction("deleted_at IS NULL")
data class PaymentNotification(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    // Default 1 (legacy group) keeps the SQS path working until Task 3 wires the API key.
    @Column(name = "group_id", nullable = false)
    val groupId: Long = 1,

    @Column(name = "card_last_digits", length = 4, nullable = true)
    val cardLastDigits: String? = null,

    @Column(name = "purchased_at", nullable = false)
    val purchasedAt: LocalDateTime,

    @Column(nullable = false, precision = 19, scale = 2)
    val amount: BigDecimal,

    @Column(name = "merchant_name", nullable = false)
    val merchantName: String,

    @Column(name = "number_of_installments", nullable = false)
    val numberOfInstallments: Int = 1,

    @Column(name = "current_installment_number")
    val currentInstallmentNumber: Int? = null,

    @Column(name = "origin", nullable = false)
    val origin: String = "HTTP_REQUEST",

    @Column(name = "origin_type", nullable = false)
    val originType: String = "HTTP_REQUEST",

    @Column(name = "category", length = 50)
    val category: String? = null,

    @Column(name = "category_id")
    val categoryId: Long? = null,

    @Column(name = "payment_method_id")
    val paymentMethodId: Long? = null,

    @Column(name = "sub_card_id")
    val subCardId: Long? = null,

    @Column(name = "description")
    val description: String? = null,

    @Column(name = "purchase_invoice_id", nullable = true)
    var purchaseInvoiceId: Long? = null,

    @Column(name = "cancelled_at")
    val cancelledAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    val deletedAt: LocalDateTime? = null,

    @Column(name = "created_at", insertable = false, updatable = false)
    val createdAt: LocalDateTime? = null,

    @Column(name = "updated_at", insertable = false, updatable = false)
    val updatedAt: LocalDateTime? = null,

)
