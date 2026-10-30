package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadData
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadResult
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.ReadOutcome
import br.com.nomar.controlai.domain.purchases_invoices.exception.InvoiceReadRejectedException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ProcessInvoiceReadResultGateway
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class ProcessInvoiceReadResultUseCaseTest {

    private val meterRegistry = SimpleMeterRegistry()

    private fun counter(outcome: String) =
        meterRegistry.find(InvoiceReadMetric.NAME).tags(InvoiceReadMetric.TAG, outcome).counter()?.count()

    private fun useCase(gateway: ProcessInvoiceReadResultGateway) = ProcessInvoiceReadResultUseCase(gateway, meterRegistry)

    private val data = InvoiceReadData(
        accessKey = ACCESS_KEY,
        merchantName = "Mercado Exemplo",
        cnpj = "12.345.678/0001-90",
        merchantAddress = "Rua Exemplo, 100",
        totalItems = 1,
        subtotal = BigDecimal("10.00"),
        discount = BigDecimal.ZERO,
        taxes = BigDecimal("1.00"),
        total = BigDecimal("10.00"),
        change = BigDecimal.ZERO,
        issuedAt = OffsetDateTime.parse("2026-10-20T14:32:10-03:00"),
        items = listOf(PurchaseItem("Arroz", "789", BigDecimal.ONE, "UN", BigDecimal("10.00"), BigDecimal("10.00"))),
        payments = listOf(PurchasePayment("Dinheiro", BigDecimal("10.00"))),
    )

    @Test
    fun `a valid result should be saved and counted as processed`() {
        var received: Pair<Long, InvoiceReadResult>? = null
        val useCase = useCase { invoiceId, result ->
            received = invoiceId to result
            Result.success(ReadOutcome.PROCESSED)
        }

        assertEquals(ReadOutcome.PROCESSED, useCase.execute(7L, data).getOrThrow())
        assertEquals(7L, received!!.first)
        assertEquals(ACCESS_KEY, received!!.second.accessKey.value)
        assertEquals(1.0, counter(InvoiceReadMetric.PROCESSED))
    }

    @Test
    fun `a result for an invoice no longer pending should be counted as discarded`() {
        val useCase = useCase { _, _ -> Result.success(ReadOutcome.DISCARDED) }

        assertEquals(ReadOutcome.DISCARDED, useCase.execute(7L, data).getOrThrow())
        assertEquals(1.0, counter(InvoiceReadMetric.DISCARDED))
        assertNull(counter(InvoiceReadMetric.PROCESSED))
    }

    @Test
    fun `an inconsistent result should be rejected without calling the gateway`() {
        var called = false
        val useCase = useCase { _, _ -> called = true; Result.success(ReadOutcome.PROCESSED) }

        val ex = useCase.execute(7L, data.copy(total = BigDecimal("12.00"))).exceptionOrNull()

        assertIs<InvoiceReadRejectedException>(ex)
        assertEquals(InvoiceReadFailureReason.INCONSISTENT_DATA, ex.reason)
        assertFalse(called)
        assertEquals(1.0, counter(InvoiceReadMetric.REJECTED))
    }

    @Test
    fun `a malformed access key should fail without being counted as rejected`() {
        val useCase = useCase { _, _ -> Result.success(ReadOutcome.PROCESSED) }

        assertIs<IllegalArgumentException>(useCase.execute(7L, data.copy(accessKey = "123")).exceptionOrNull())
        assertNull(counter(InvoiceReadMetric.REJECTED))
    }

    @Test
    fun `a gateway failure should be returned without metrics`() {
        val useCase = useCase { _, _ -> Result.failure(IllegalStateException("db down")) }

        assertIs<IllegalStateException>(useCase.execute(7L, data).exceptionOrNull())
        assertNull(meterRegistry.find(InvoiceReadMetric.NAME).counter())
    }

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
    }
}
