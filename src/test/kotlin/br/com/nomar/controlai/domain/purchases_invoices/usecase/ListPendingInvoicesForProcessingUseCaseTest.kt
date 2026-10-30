package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.PendingInvoiceQueue
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPendingInvoicesForProcessingUseCase.Companion.PENDING_GAUGE_NAME
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class ListPendingInvoicesForProcessingUseCaseTest {

    private val meterRegistry = SimpleMeterRegistry()

    private fun gauge() = meterRegistry.find(PENDING_GAUGE_NAME).gauge()?.value()

    private fun item(id: Long) = PendingInvoiceQueue.Item(id, "https://sefaz/$id", "3".repeat(44), Instant.EPOCH)

    @Test
    fun `should pass the limit and set the pending gauge to the whole queue size`() {
        var receivedLimit = 0
        val useCase = ListPendingInvoicesForProcessingUseCase({ limit ->
            receivedLimit = limit
            Result.success(PendingInvoiceQueue(listOf(item(1), item(2)), remaining = 5))
        }, meterRegistry)

        val queue = useCase.execute(2).getOrThrow()

        assertEquals(2, receivedLimit)
        assertEquals(listOf(1L, 2L), queue.items.map { it.id })
        assertEquals(7.0, gauge())
    }

    @Test
    fun `a failed listing should keep the last known gauge value`() {
        var fail = false
        val useCase = ListPendingInvoicesForProcessingUseCase({
            if (fail) Result.failure(IllegalStateException("down")) else Result.success(PendingInvoiceQueue(listOf(item(1)), 0))
        }, meterRegistry)
        useCase.execute(50)
        fail = true

        useCase.execute(50)

        assertEquals(1.0, gauge())
    }
}
