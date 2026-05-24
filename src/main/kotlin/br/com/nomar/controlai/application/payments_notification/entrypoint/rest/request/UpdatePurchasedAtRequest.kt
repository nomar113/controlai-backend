package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class UpdatePurchasedAtRequest(
    @field:NotNull
    val purchasedAt: LocalDateTime,
)
