package br.com.nomar.controlai.domain.purchases_invoices.entity

import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import br.com.nomar.controlai.domain.purchases_invoices.exception.InvoiceReadRejectedException
import java.math.BigDecimal
import java.time.Instant

// Invoice data read by the NFC-e processor that passed the consistency rules of the techspec. The
// processor applies the same rules, but the endpoint is open, so the server never trusts the client.
class InvoiceReadResult private constructor(
    val accessKey: AccessKey,
    val merchantName: String,
    val merchantAddress: String,
    val cnpj: Cnpj,
    val totalItems: TotalItems,
    val subtotal: BigDecimal,
    val discount: BigDecimal,
    val taxes: BigDecimal,
    val total: BigDecimal,
    val issuedAt: Instant,
    val items: List<PurchaseItem>,
    val payments: List<PurchasePayment>,
) {

    companion object {
        // Rounding of cents between the values printed on the SEFAZ page
        val TOLERANCE = BigDecimal("0.02")

        // Failure: InvoiceReadRejectedException with INCOMPLETE_DATA (rules 1-2) or INCONSISTENT_DATA
        // (rules 3-5); IllegalArgumentException when accessKey is not a 44-digit key
        fun of(data: InvoiceReadData): InvoiceReadResult {
            val accessKey = AccessKey.of(data.accessKey)
            val issuedAt = requireComplete(data)
            requireConsistent(data)
            return InvoiceReadResult(
                accessKey = accessKey,
                merchantName = data.merchantName,
                merchantAddress = data.merchantAddress,
                cnpj = Cnpj.of(data.cnpj),
                totalItems = TotalItems.of(data.totalItems),
                subtotal = data.subtotal,
                discount = data.discount,
                taxes = data.taxes,
                total = data.total,
                issuedAt = issuedAt,
                items = data.items,
                payments = data.payments,
            )
        }

        // Returns the issue date as an instant, the only rule 1 field that changes type
        private fun requireComplete(data: InvoiceReadData): Instant {
            incompleteUnless(data.items.isNotEmpty(), "A nota não tem itens")
            incompleteUnless(data.total > BigDecimal.ZERO, "A nota não tem total")
            val issuedAt = data.issuedAt ?: throw incomplete("A nota não tem data de emissão")
            incompleteUnless(isValidCnpj(data.cnpj), "O CNPJ do estabelecimento é inválido")
            incompleteUnless(data.items.size == data.totalItems, "A quantidade de itens não confere com o total de itens")
            return issuedAt.toInstant()
        }

        private fun requireConsistent(data: InvoiceReadData) {
            inconsistentUnless(itemsMatchSubtotal(data), "A soma dos itens não confere com o subtotal")
            inconsistentUnless(subtotalMinusDiscountMatchesTotal(data), "O subtotal menos o desconto não confere com o total")
            inconsistentUnless(paymentsMatchTotal(data), "A soma dos pagamentos menos o troco não confere com o total")
        }

        private fun itemsMatchSubtotal(data: InvoiceReadData): Boolean =
            data.items.sumOf { it.totalPrice } approximately data.subtotal

        private fun subtotalMinusDiscountMatchesTotal(data: InvoiceReadData): Boolean =
            data.subtotal - data.discount approximately data.total

        private fun paymentsMatchTotal(data: InvoiceReadData): Boolean =
            data.payments.isNotEmpty() && data.payments.sumOf { it.value } - data.change approximately data.total

        private fun isValidCnpj(value: String): Boolean = runCatching { Cnpj.of(value) }.isSuccess

        private infix fun BigDecimal.approximately(other: BigDecimal): Boolean =
            (this - other).abs() <= TOLERANCE

        private fun incompleteUnless(condition: Boolean, message: String) {
            if (!condition) throw incomplete(message)
        }

        private fun incomplete(message: String) =
            InvoiceReadRejectedException(InvoiceReadFailureReason.INCOMPLETE_DATA, message)

        private fun inconsistentUnless(condition: Boolean, message: String) {
            if (!condition) throw InvoiceReadRejectedException(InvoiceReadFailureReason.INCONSISTENT_DATA, message)
        }
    }
}
