package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response

import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchasePayment
import java.math.BigDecimal

data class ExtractedPurchaseInvoiceResponse(
    val merchantName: String,
    val cnpj: String,
    val merchantAddress: String,
    val totalItems: Int,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val total: BigDecimal,
    val taxes: BigDecimal,
    val date: String,
    val items: List<ExtractedPurchaseItemResponse>,
    val payments: List<ExtractedPurchasePaymentResponse>,
) {
    companion object {
        fun from(invoice: ExtractedPurchaseInvoice) = ExtractedPurchaseInvoiceResponse(
            merchantName = invoice.merchantName,
            cnpj = invoice.cnpj,
            merchantAddress = invoice.merchantAddress,
            totalItems = invoice.totalItems,
            subtotal = invoice.subtotal,
            discount = invoice.discount,
            total = invoice.total,
            taxes = invoice.taxes,
            date = invoice.date,
            items = invoice.items.map(ExtractedPurchaseItemResponse::from),
            payments = invoice.payments.map(ExtractedPurchasePaymentResponse::from),
        )
    }
}

data class ExtractedPurchaseItemResponse(
    val productName: String,
    val code: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPrice: BigDecimal,
    val totalPrice: BigDecimal,
) {
    companion object {
        fun from(item: ExtractedPurchaseItem) = ExtractedPurchaseItemResponse(
            productName = item.productName,
            code = item.code,
            quantity = item.quantity,
            unit = item.unit,
            unitPrice = item.unitPrice,
            totalPrice = item.totalPrice,
        )
    }
}

data class ExtractedPurchasePaymentResponse(
    val type: String,
    val value: BigDecimal,
) {
    companion object {
        fun from(payment: ExtractedPurchasePayment) = ExtractedPurchasePaymentResponse(
            type = payment.type,
            value = payment.value,
        )
    }
}
