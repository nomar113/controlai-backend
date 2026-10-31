package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.InvoiceNoticeRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DismissProcessedInvoicesNoticeGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DismissProcessedInvoicesNoticeProvider(
    private val invoiceNoticeRepository: InvoiceNoticeRepository,
    private val requestContext: RequestContext,
) : DismissProcessedInvoicesNoticeGateway {

    @Transactional
    override fun execute(): Result<Unit> = runCatching {
        invoiceNoticeRepository.dismissNotice(requestContext.userId)
        Unit
    }
}
