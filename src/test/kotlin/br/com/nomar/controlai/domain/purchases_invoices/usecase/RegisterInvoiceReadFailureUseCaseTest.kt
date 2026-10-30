package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.FailureOutcome
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadFailureReason
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RegisterInvoiceReadFailureGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.RegisterInvoiceReadFailureUseCase.Companion.METRIC_NAME
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull

class RegisterInvoiceReadFailureUseCaseTest {

    private val meterRegistry = SimpleMeterRegistry()

    private fun counter(outcome: String) =
        meterRegistry.find(METRIC_NAME).tags("outcome", outcome).counter()?.count()

    private fun useCase(gateway: RegisterInvoiceReadFailureGateway) =
        RegisterInvoiceReadFailureUseCase(gateway, meterRegistry, maxReadAttempts = MAX_ATTEMPTS)

    @Test
    fun `below the limit should keep the invoice for a retry and count a failed read`() {
        var received: List<Any>? = null
        val useCase = useCase { invoiceId, accessKey, reason, maxAttempts ->
            received = listOf(invoiceId, accessKey.value, reason, maxAttempts)
            Result.success(FailureOutcome(FailureOutcome.Outcome.RETRY_SCHEDULED, 1))
        }

        val result = useCase.execute(10L, ACCESS_KEY, InvoiceReadFailureReason.TIMEOUT).getOrThrow()

        assertEquals(FailureOutcome(FailureOutcome.Outcome.RETRY_SCHEDULED, 1), result)
        assertEquals(listOf(10L, ACCESS_KEY, InvoiceReadFailureReason.TIMEOUT, MAX_ATTEMPTS), received)
        assertEquals(1.0, counter("failed"))
        assertNull(counter("read_failed"))
    }

    @Test
    fun `at the limit should report READ_FAILED and count it apart from the retryable failures`() {
        val useCase = useCase { _, _, _, _ -> Result.success(FailureOutcome(FailureOutcome.Outcome.READ_FAILED, 3)) }

        val result = useCase.execute(10L, ACCESS_KEY, InvoiceReadFailureReason.UNEXPECTED_LAYOUT).getOrThrow()

        assertEquals(FailureOutcome(FailureOutcome.Outcome.READ_FAILED, 3), result)
        assertEquals(1.0, counter("read_failed"))
        assertNull(counter("failed"))
    }

    @Test
    fun `a discarded failure should succeed without error and count as discarded`() {
        val useCase = useCase { _, _, _, _ -> Result.success(FailureOutcome.NOTHING_CHANGED) }

        val result = useCase.execute(10L, ACCESS_KEY, InvoiceReadFailureReason.NOT_FOUND).getOrThrow()

        assertEquals(FailureOutcome.Outcome.DISCARDED, result.outcome)
        assertEquals(1.0, counter("discarded"))
    }

    @Test
    fun `an invalid access key should fail without reaching the gateway`() {
        var called = false
        val useCase = useCase { _, _, _, _ ->
            called = true
            Result.success(FailureOutcome.NOTHING_CHANGED)
        }

        val result = useCase.execute(10L, "123", InvoiceReadFailureReason.TIMEOUT)

        assertIs<IllegalArgumentException>(result.exceptionOrNull())
        assertFalse(called)
        assertNull(counter("discarded"))
    }

    @Test
    fun `a max attempts below 1 should be rejected at startup`() {
        assertFailsWith<IllegalArgumentException> {
            RegisterInvoiceReadFailureUseCase({ _, _, _, _ -> Result.success(FailureOutcome.NOTHING_CHANGED) }, meterRegistry, 0)
        }
    }

    @Test
    fun `a gateway error should be propagated without recording an outcome`() {
        val error = IllegalStateException("database down")
        val useCase = useCase { _, _, _, _ -> Result.failure(error) }

        val result = useCase.execute(10L, ACCESS_KEY, InvoiceReadFailureReason.TIMEOUT)

        assertEquals(error, result.exceptionOrNull())
        assertNull(meterRegistry.find(METRIC_NAME).counter())
    }

    private companion object {
        const val MAX_ATTEMPTS = 3
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
    }
}
