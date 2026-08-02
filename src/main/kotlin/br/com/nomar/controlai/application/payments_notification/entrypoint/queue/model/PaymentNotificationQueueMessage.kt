package br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model

import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentNotificationQueueMessage(
    val text: String? = null,
    val origin: String,
    val originType: String = "HTTP_REQUEST",
    val cardLastDigits: String? = null,
    val purchasedAt: LocalDateTime? = null,
    val amount: BigDecimal? = null,
    val merchantName: String? = null,
    val numberOfInstallments: Int? = null,
    val currentInstallmentNumber: Int? = null,
    // Null in messages enqueued before Task 3; listener falls back to legacy group (id 1)
    val groupId: Long? = null,
)
