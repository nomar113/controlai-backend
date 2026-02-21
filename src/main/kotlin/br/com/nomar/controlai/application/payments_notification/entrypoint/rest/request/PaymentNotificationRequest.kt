package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import java.math.BigDecimal
import java.time.LocalDateTime

data class PaymentNotificationRequest(
    val cardLastDigits: String,
    val purchasedAt: LocalDateTime,
    val amount: BigDecimal,
    val merchantName: String
)
