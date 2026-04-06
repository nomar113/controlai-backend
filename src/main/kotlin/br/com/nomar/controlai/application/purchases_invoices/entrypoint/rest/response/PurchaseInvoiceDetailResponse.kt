package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseItemModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchasePaymentModel
import java.math.BigDecimal
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
    val items: List<PurchaseItemResponse>,
    val payments: List<PurchasePaymentResponse>,
) {
    companion object {
        fun from(
            invoice: PurchaseInvoiceModel,
            items: List<PurchaseItemModel>,
            payments: List<PurchasePaymentModel>,
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
            items = items.map(PurchaseItemResponse::from),
            payments = payments.map(PurchasePaymentResponse::from),
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
