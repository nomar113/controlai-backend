package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.GetProcessedInvoicesNoticeGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RetryInvoiceReadGateway
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RetryInvoiceReadUseCaseTest {

    // Same rule as RetryInvoiceReadProvider: only a READ_FAILED invoice of the group can be requeued
    private class FakeInvoices(var status: String, val inGroup: Boolean = true) : RetryInvoiceReadGateway {
        override fun execute(invoiceId: Long): Result<Unit> = runCatching {
            if (!inGroup) throw NoSuchElementException("not found")
            check(status == "READ_FAILED") { "not READ_FAILED" }
            status = "PENDING"
        }
    }

    @Test
    fun `should requeue a READ_FAILED invoice`() {
        val invoices = FakeInvoices("READ_FAILED")

        assertTrue(RetryInvoiceReadUseCase(invoices).execute(1L).isSuccess)
        assertEquals("PENDING", invoices.status)
    }

    @Test
    fun `should fail with IllegalStateException for any other status`() {
        listOf("PENDING", "PROCESSED").forEach { status ->
            val invoices = FakeInvoices(status)

            val ex = RetryInvoiceReadUseCase(invoices).execute(1L).exceptionOrNull()

            assertIs<IllegalStateException>(ex)
            assertEquals(status, invoices.status)
        }
    }

    @Test
    fun `should fail with NoSuchElementException outside the group`() {
        val ex = RetryInvoiceReadUseCase(FakeInvoices("READ_FAILED", inGroup = false)).execute(1L).exceptionOrNull()

        assertIs<NoSuchElementException>(ex)
    }

    @Test
    fun `notice use case should return the count of the gateway`() {
        val result = GetProcessedInvoicesNoticeUseCase(GetProcessedInvoicesNoticeGateway { Result.success(3) }).execute()

        assertEquals(3, result.getOrThrow())
    }
}
