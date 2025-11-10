package br.com.nomar.controlai.application.purchase.entrypoint.rest.request


import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseRequest(
    val cardLastDigits: String,
    val purchasedAt: LocalDateTime,
    val amount: BigDecimal,
    val merchantName: String
)