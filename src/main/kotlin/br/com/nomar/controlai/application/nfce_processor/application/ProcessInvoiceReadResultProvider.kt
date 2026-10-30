package br.com.nomar.controlai.application.nfce_processor.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseItemModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchasePaymentModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadResult
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.ReadOutcome
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ProcessInvoiceReadResultGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.math.RoundingMode

@Component
class ProcessInvoiceReadResultProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val purchasePaymentRepository: PurchasePaymentRepository,
    private val transactionTemplate: TransactionTemplate,
) : ProcessInvoiceReadResultGateway {

    // Header, items and payments share one transaction: any exception rolls everything back and the
    // invoice stays PENDING without partial data
    override fun execute(invoiceId: Long, result: InvoiceReadResult): Result<ReadOutcome> = runCatching {
        transactionTemplate.execute { process(invoiceId, result) }!!
    }

    private fun process(invoiceId: Long, result: InvoiceReadResult): ReadOutcome {
        if (markProcessed(invoiceId, result) == 0) return ReadOutcome.DISCARDED

        purchaseItemRepository.saveAll(result.items.map { it.toModel(invoiceId) })
        purchasePaymentRepository.saveAll(result.payments.map { it.toModel(invoiceId) })
        return ReadOutcome.PROCESSED
    }

    private fun markProcessed(invoiceId: Long, result: InvoiceReadResult): Int =
        purchaseInvoiceRepository.markProcessed(
            id = invoiceId,
            accessKey = result.accessKey.value,
            date = result.issuedAt,
            merchantName = result.merchantName,
            merchantAddress = result.merchantAddress,
            cnpj = result.cnpj.value,
            totalItems = result.totalItems.value,
            subtotal = result.subtotal,
            total = result.total,
            taxes = result.taxes,
            discount = result.discount,
        )

    private fun PurchaseItem.toModel(invoiceId: Long) = PurchaseItemModel(
        productName = productName,
        code = code,
        quantity = quantity,
        unit = unit,
        // The SEFAZ may print the unit price with more decimals than the decimal(10,2) column
        unitPrice = unitPrice.setScale(PRICE_SCALE, RoundingMode.HALF_UP),
        totalPrice = totalPrice,
        purchaseInvoiceId = invoiceId,
    )

    private fun PurchasePayment.toModel(invoiceId: Long) = PurchasePaymentModel(
        type = type,
        value = value,
        purchaseInvoiceId = invoiceId,
    )

    private companion object {
        const val PRICE_SCALE = 2
    }
}
