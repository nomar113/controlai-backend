package br.com.nomar.controlai.domain.purchases_invoices.entity

import br.com.nomar.controlai.domain.purchases_invoices.exception.InvoiceReadRejectedException
import java.math.BigDecimal
import java.time.Instant
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class InvoiceReadResultTest {

    // Items 10.00 + 5.50 = subtotal 15.50, minus discount 0.50 = total 15.00, paid 20.00 with 5.00 change
    private fun data(
        totalItems: Int = 2,
        subtotal: String = "15.50",
        discount: String = "0.50",
        total: String = "15.00",
        change: String = "5.00",
        cnpj: String = "12.345.678/0001-90",
        issuedAt: OffsetDateTime? = OffsetDateTime.parse("2026-10-20T14:32:10-03:00"),
        items: List<PurchaseItem> = listOf(item("10.00"), item("5.50")),
        payments: List<PurchasePayment> = listOf(PurchasePayment("Cartão de Crédito", BigDecimal("20.00"))),
    ) = InvoiceReadData(
        accessKey = ACCESS_KEY,
        merchantName = "Mercado Exemplo",
        cnpj = cnpj,
        merchantAddress = "Rua Exemplo, 100",
        totalItems = totalItems,
        subtotal = BigDecimal(subtotal),
        discount = BigDecimal(discount),
        taxes = BigDecimal("1.20"),
        total = BigDecimal(total),
        change = BigDecimal(change),
        issuedAt = issuedAt,
        items = items,
        payments = payments,
    )

    private fun item(totalPrice: String) =
        PurchaseItem("Produto", "789", BigDecimal("1.000"), "UN", BigDecimal(totalPrice), BigDecimal(totalPrice))

    private fun assertRejected(reason: InvoiceReadFailureReason, data: InvoiceReadData) {
        val ex = assertFailsWith<InvoiceReadRejectedException> { InvoiceReadResult.of(data) }
        assertEquals(reason, ex.reason)
    }

    @Test
    fun `consistent data should build the result with issuedAt as an instant`() {
        val result = InvoiceReadResult.of(data())

        assertEquals(ACCESS_KEY, result.accessKey.value)
        assertEquals("12.345.678/0001-90", result.cnpj.value)
        assertEquals(2, result.totalItems.value)
        assertEquals(BigDecimal("15.00"), result.total)
        assertEquals(Instant.parse("2026-10-20T17:32:10Z"), result.issuedAt)
        assertEquals(2, result.items.size)
        assertEquals(1, result.payments.size)
    }

    @Test
    fun `rule 1 - no items should be incomplete`() =
        assertRejected(InvoiceReadFailureReason.INCOMPLETE_DATA, data(items = emptyList(), totalItems = 0))

    @Test
    fun `rule 1 - zero total should be incomplete`() =
        assertRejected(InvoiceReadFailureReason.INCOMPLETE_DATA, data(subtotal = "0.50", total = "0.00"))

    @Test
    fun `rule 1 - missing issuedAt should be incomplete`() =
        assertRejected(InvoiceReadFailureReason.INCOMPLETE_DATA, data(issuedAt = null))

    @Test
    fun `rule 1 - invalid cnpj should be incomplete`() =
        assertRejected(InvoiceReadFailureReason.INCOMPLETE_DATA, data(cnpj = "12345678000190"))

    @Test
    fun `rule 1 - alphanumeric cnpj should be accepted`() {
        assertEquals("12.ABC.345/01DE-35", InvoiceReadResult.of(data(cnpj = "12.ABC.345/01DE-35")).cnpj.value)
    }

    @Test
    fun `rule 2 - items count different from totalItems should be incomplete`() =
        assertRejected(InvoiceReadFailureReason.INCOMPLETE_DATA, data(totalItems = 3))

    @Test
    fun `rule 3 - items sum different from subtotal should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(subtotal = "16.50", total = "16.00", change = "4.00"))

    @Test
    fun `rule 3 - items sum within 0,02 of the subtotal should pass`() {
        InvoiceReadResult.of(data(subtotal = "15.52", total = "15.02", change = "4.98"))
    }

    @Test
    fun `rule 3 - items sum 0,03 away from the subtotal should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(subtotal = "15.53", total = "15.03", change = "4.97"))

    @Test
    fun `rule 4 - subtotal minus discount within 0,02 of the total should pass`() {
        InvoiceReadResult.of(data(total = "15.02", change = "4.98"))
    }

    @Test
    fun `rule 4 - subtotal minus discount 0,03 away from the total should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(total = "15.03", change = "4.97"))

    @Test
    fun `rule 5 - payments minus change within 0,02 of the total should pass`() {
        InvoiceReadResult.of(data(change = "4.98"))
    }

    @Test
    fun `rule 5 - payments minus change 0,03 away from the total should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(change = "4.97"))

    @Test
    fun `rule 5 - payments without discounting the change should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(change = "0.00"))

    @Test
    fun `rule 5 - several payments should be summed`() {
        val payments = listOf(
            PurchasePayment("Dinheiro", BigDecimal("10.00")),
            PurchasePayment("Cartão de Débito", BigDecimal("5.00")),
        )
        InvoiceReadResult.of(data(change = "0.00", payments = payments))
    }

    @Test
    fun `rule 5 - no payments should be inconsistent`() =
        assertRejected(InvoiceReadFailureReason.INCONSISTENT_DATA, data(payments = emptyList(), change = "0.00"))

    @Test
    fun `malformed access key should fail with IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> { InvoiceReadResult.of(data().copy(accessKey = "123")) }
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
    }
}
