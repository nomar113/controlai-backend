package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import java.math.BigDecimal
import java.time.LocalDateTime

data class PurchaseResponse(
    val id: Long?,
    val date: LocalDateTime,
    val merchantName: String?,
    val totalItems: Int?,
    val total: BigDecimal?,
) {
    companion object {
        fun from(purchase: Purchase) = PurchaseResponse(
            id = purchase.id,
            date = purchase.date,
            merchantName = purchase.merchantName,
            totalItems = purchase.totalItems,
            total = purchase.total,
        )
    }
}
