package br.com.nomar.controlai.domain.purchases_invoices.entity

import java.math.BigDecimal
import java.time.LocalDateTime

class Purchase(
    val id: Long?,
    val date: LocalDateTime,
    val total: BigDecimal?,
    val merchantName: String?,
    val totalItems: Int?,
)
