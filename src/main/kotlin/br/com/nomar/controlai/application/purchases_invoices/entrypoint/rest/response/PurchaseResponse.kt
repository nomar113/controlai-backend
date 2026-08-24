package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

data class PurchaseResponse(
    val id: Long?,
    val date: Instant,
    val merchantName: String?,
    val totalItems: Int?,
    val total: BigDecimal?,
    val description: String? = null,
    val categoryName: String? = null,
    val categoryId: Long? = null,
    val cancelledAt: Instant? = null,
) {
    companion object {
        // Purchase.date/cancelledAt are still LocalDateTime (task 2.0 didn't cover this projection),
        // but their source columns (payment_notifications.purchased_at / .cancelled_at and
        // purchase_invoices.date / .cancelled_at) are always written with an explicit UTC instant, so
        // their wall-clock value already equals the UTC instant.
        fun from(purchase: Purchase) = PurchaseResponse(
            id = purchase.id,
            date = purchase.date.atZone(ZoneOffset.UTC).toInstant(),
            merchantName = purchase.merchantName,
            totalItems = purchase.totalItems,
            total = purchase.total,
            description = purchase.description,
            categoryName = purchase.categoryName,
            categoryId = purchase.categoryId,
            cancelledAt = purchase.cancelledAt?.atZone(ZoneOffset.UTC)?.toInstant(),
        )
    }
}
