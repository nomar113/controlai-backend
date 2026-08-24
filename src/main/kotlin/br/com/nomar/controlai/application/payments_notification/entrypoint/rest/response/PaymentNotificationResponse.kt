package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response

import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset

data class PaymentNotificationResponse(
    val id: Long,
    val cardLastDigits: String? = null,
    val purchasedAt: Instant,
    val amount: BigDecimal,
    val merchantName: String,
    val numberOfInstallments: Int,
    val currentInstallmentNumber: Int? = null,
    val category: String? = null,
    val categoryId: Long? = null,
    val paymentMethodId: Long? = null,
    val subCardId: Long? = null,
    val description: String? = null,
    val origin: String? = null,
    val originType: String? = null,
    val cancelledAt: Instant? = null,
    val installments: List<InstallmentResponse> = emptyList(),
    val purchaseInvoiceId: Long? = null,
    val associatedInvoice: AssociatedInvoiceResponse? = null,
    // The installment actually due in the month being listed (see
    // PaymentNotificationController.listNotifications) — the monthly listing shows this
    // instead of `amount` so a parceled purchase doesn't overstate the month's spend with
    // its full total.
    val installmentAmount: BigDecimal? = null,
    val installmentNumberForMonth: Int? = null,
) {
    companion object {
        fun from(
            entity: PaymentNotification,
            invoice: PurchaseInvoiceModel? = null,
            installmentForMonth: Installment? = null,
        ) = PaymentNotificationResponse(
            id = entity.id,
            cardLastDigits = entity.cardLastDigits,
            purchasedAt = entity.purchasedAt,
            amount = entity.amount,
            merchantName = entity.merchantName,
            numberOfInstallments = entity.numberOfInstallments,
            currentInstallmentNumber = entity.currentInstallmentNumber,
            category = entity.category?.name,
            categoryId = entity.category?.id,
            paymentMethodId = entity.paymentMethodId,
            subCardId = entity.subCardId,
            description = entity.description,
            origin = entity.origin,
            originType = entity.originType,
            // PaymentNotification.cancelledAt is still LocalDateTime (task 2.0 didn't cover it), but
            // CancelPaymentNotificationProvider pins it to LocalDateTime.now(ZoneOffset.UTC) at write
            // time, so its wall-clock value is already the UTC instant.
            cancelledAt = entity.cancelledAt?.atZone(ZoneOffset.UTC)?.toInstant(),
            purchaseInvoiceId = entity.purchaseInvoiceId,
            associatedInvoice = invoice?.let { AssociatedInvoiceResponse.from(it) },
            installmentAmount = installmentForMonth?.amount,
            installmentNumberForMonth = installmentForMonth?.installmentNumber,
        )
    }
}
