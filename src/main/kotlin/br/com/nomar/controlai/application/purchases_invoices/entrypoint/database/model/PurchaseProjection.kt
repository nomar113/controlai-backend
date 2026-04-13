package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model

import java.math.BigDecimal
import java.time.LocalDateTime

interface PurchaseProjection {
    fun getId(): Long
    fun getDate(): LocalDateTime
    fun getTotal(): BigDecimal?
    fun getMerchantName(): String?
    fun getTotalItems(): Int?
    fun getDescription(): String?
    fun getCategoryName(): String?
}
