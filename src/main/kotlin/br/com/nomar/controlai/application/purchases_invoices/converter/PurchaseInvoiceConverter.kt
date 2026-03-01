package br.com.nomar.controlai.application.purchases_invoices.converter

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseItemModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchasePaymentModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import org.springframework.stereotype.Component

@Component
class PurchaseInvoiceConverter {

    fun toModel(entity: PurchaseInvoice): PurchaseInvoiceModel {
        return PurchaseInvoiceModel(
            id = entity.id,
            date = entity.date,
            merchantName = entity.merchantName,
            merchantAddress = entity.merchantAddress,
            cnpj = entity.cnpj.value,
            totalItems = entity.totalItems.value,
            invoiceUrl = entity.invoiceUrl.value,
            accessKey = entity.accessKey.value,
            subtotal = entity.subtotal,
            taxes = entity.taxes,
            discount = entity.discount,
            total = entity.total,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(model: PurchaseInvoiceModel): PurchaseInvoice {
        return PurchaseInvoice(
            id = model.id,
            date = model.date,
            merchantName = model.merchantName
                ?: throw IllegalStateException("merchantName não pode ser null"),
            merchantAddress = model.merchantAddress
                ?: throw IllegalStateException("merchantAddress não pode ser null"),
            cnpj = Cnpj.of(
                model.cnpj
                    ?: throw IllegalStateException("cnpj não pode ser null")
            ),
            totalItems = TotalItems.of(
                model.totalItems
                    ?: throw IllegalStateException("totalItems não pode ser null")
            ),
            invoiceUrl = InvoiceUrl.of(
                model.invoiceUrl
                    ?: throw IllegalStateException("invoiceUrl não pode ser null")
            ),
            accessKey = AccessKey.of(
                model.accessKey
                    ?: throw IllegalStateException("accessKey não pode ser null")
            ),
            subtotal = model.subtotal
                ?: throw IllegalStateException("subtotal não pode ser null"),
            total = model.total
                ?: throw IllegalStateException("total não pode ser null"),
            taxes = model.taxes
                ?: throw IllegalStateException("taxes não pode ser null"),
            discount = model.discount
                ?: throw IllegalStateException("discount não pode ser null"),
            items = emptyList(),
            payments = emptyList(),
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }

    fun toItemModels(items: List<PurchaseItem>, purchaseInvoiceId: Long): List<PurchaseItemModel> {
        return items.map { item ->
            PurchaseItemModel(
                productName = item.productName,
                code = item.code,
                quantity = item.quantity,
                unit = item.unit,
                unitPrice = item.unitPrice,
                totalPrice = item.totalPrice,
                purchaseInvoiceId = purchaseInvoiceId,
            )
        }
    }

    fun toPaymentModels(payments: List<PurchasePayment>, purchaseInvoiceId: Long): List<PurchasePaymentModel> {
        return payments.map { payment ->
            PurchasePaymentModel(
                type = payment.type,
                value = payment.value,
                purchaseInvoiceId = purchaseInvoiceId,
            )
        }
    }

    fun toEntity(
        model: PurchaseInvoiceModel,
        items: List<PurchaseItem>,
        payments: List<PurchasePayment>,
    ): PurchaseInvoice {
        return toEntity(model).copyWith(items = items, payments = payments)
    }

    private fun PurchaseInvoice.copyWith(
        items: List<PurchaseItem>,
        payments: List<PurchasePayment>,
    ): PurchaseInvoice {
        return PurchaseInvoice(
            id = id,
            date = date,
            merchantName = merchantName,
            merchantAddress = merchantAddress,
            cnpj = cnpj,
            totalItems = totalItems,
            invoiceUrl = invoiceUrl,
            accessKey = accessKey,
            subtotal = subtotal,
            total = total,
            taxes = taxes,
            discount = discount,
            items = items,
            payments = payments,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }
}
