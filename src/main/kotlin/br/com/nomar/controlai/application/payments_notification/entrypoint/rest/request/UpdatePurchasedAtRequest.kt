package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import jakarta.validation.constraints.NotNull
import java.time.Instant

data class UpdatePurchasedAtRequest(
    @field:NotNull
    @field:JsonDeserialize(using = PurchasedAtDeserializer::class)
    val purchasedAt: Instant,
)
