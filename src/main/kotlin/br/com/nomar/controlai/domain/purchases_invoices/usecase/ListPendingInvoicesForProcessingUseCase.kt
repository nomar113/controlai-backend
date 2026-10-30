package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.entity.PendingInvoiceQueue
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPendingInvoicesForProcessingGateway
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong

// Queue of the NFC-e processor. Open endpoint (no credential): the queue carries no group or user data.
@Component
class ListPendingInvoicesForProcessingUseCase(
    private val listPendingInvoicesForProcessingGateway: ListPendingInvoicesForProcessingGateway,
    meterRegistry: MeterRegistry,
) {

    // Size of the queue seen by the last listing, so a scrape never queries the database. It goes
    // stale between processor runs: scans made since then are not counted.
    private val pendingInvoices = AtomicLong(0).also { value ->
        Gauge.builder(PENDING_GAUGE_NAME, value) { it.toDouble() }
            .description("PENDING invoices seen by the last NFC-e processor listing")
            .register(meterRegistry)
    }

    fun execute(limit: Int): Result<PendingInvoiceQueue> =
        listPendingInvoicesForProcessingGateway.execute(limit)
            .onSuccess { queue -> pendingInvoices.set(queue.items.size + queue.remaining) }

    companion object {
        const val PENDING_GAUGE_NAME = "controlai.invoices.pending"
    }
}
