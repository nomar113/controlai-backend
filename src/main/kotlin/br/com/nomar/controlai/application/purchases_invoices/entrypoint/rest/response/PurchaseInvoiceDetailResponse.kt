package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseItemModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchasePaymentModel
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

data class PurchaseInvoiceDetailResponse(
    val id: Long,
    val date: OffsetDateTime,
    val merchantName: String?,
    val merchantAddress: String?,
    val cnpj: String?,
    val totalItems: Int?,
    val invoiceUrl: String?,
    val accessKey: String?,
    val subtotal: BigDecimal?,
    val total: BigDecimal?,
    val taxes: BigDecimal?,
    val discount: BigDecimal?,
    val description: String?,
    val cancelledAt: String?,
    val items: List<PurchaseItemResponse>,
    val payments: List<PurchasePaymentResponse>,
    val associatedPayment: AssociatedPaymentResponse?,
) {
    companion object {
        fun from(
            invoice: PurchaseInvoiceModel,
            items: List<PurchaseItemModel>,
            payments: List<PurchasePaymentModel>,
            associatedPayment: PaymentNotification? = null,
        ) = PurchaseInvoiceDetailResponse(
            id = invoice.id!!,
            date = invoice.date,
            merchantName = invoice.merchantName,
            merchantAddress = invoice.merchantAddress,
            cnpj = invoice.cnpj,
            totalItems = invoice.totalItems,
            invoiceUrl = invoice.invoiceUrl,
            accessKey = invoice.accessKey,
            subtotal = invoice.subtotal,
            total = invoice.total,
            taxes = invoice.taxes,
            discount = invoice.discount,
            description = invoice.description,
            cancelledAt = invoice.cancelledAt?.toString(),
            items = items.map(PurchaseItemResponse::from),
            payments = payments.map(PurchasePaymentResponse::from),
            associatedPayment = associatedPayment?.let(AssociatedPaymentResponse::from),
        )
    }
}

data class AssociatedPaymentResponse(
    val id: Long,
    val merchantName: String,
    val amount: BigDecimal,
    val purchasedAt: LocalDateTime,
    val cardLastDigits: String?,
) {
    companion object {
        fun from(notification: PaymentNotification) = AssociatedPaymentResponse(
            id = notification.id,
            merchantName = notification.merchantName,
            amount = notification.amount,
            purchasedAt = notification.purchasedAt,
            cardLastDigits = notification.cardLastDigits,
        )
    }
}

data class PurchaseItemResponse(
    val id: Long,
    val productName: String,
    val code: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
) {
    companion object {
        fun from(item: PurchaseItemModel) = PurchaseItemResponse(
            id = item.id!!,
            productName = item.productName,
            code = item.code,
            quantity = item.quantity,
            unit = item.unit,
            unitPrice = item.unitPrice,
            totalPrice = item.totalPrice,
        )
    }
}

data class PurchasePaymentResponse(
    val id: Long,
    val type: String,
    val value: BigDecimal,
) {
    companion object {
        fun from(payment: PurchasePaymentModel) = PurchasePaymentResponse(
            id = payment.id!!,
            type = payment.type,
            value = payment.value,
        )
    }
}
