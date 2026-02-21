package br.com.nomar.controlai.application.purchases_invoices.converter

import br.com.nomar.controlai.domain.purchases_invoices.entity.*
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
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
            createdAt = model.createdAt,
            updatedAt = model.updatedAt
        )
    }
}