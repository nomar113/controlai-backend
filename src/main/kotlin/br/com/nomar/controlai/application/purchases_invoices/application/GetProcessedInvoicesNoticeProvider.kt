package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.InvoiceNoticeRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.gateway.GetProcessedInvoicesNoticeGateway
import org.springframework.stereotype.Component

@Component
class GetProcessedInvoicesNoticeProvider(
    private val invoiceNoticeRepository: InvoiceNoticeRepository,
    private val requestContext: RequestContext,
) : GetProcessedInvoicesNoticeGateway {

    override fun execute(): Result<Int> = runCatching {
        invoiceNoticeRepository.countProcessedSinceNotice(requestContext.userId, requestContext.groupId).toInt()
    }
}
