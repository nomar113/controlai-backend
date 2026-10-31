package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.DismissProcessedInvoicesNoticeGateway
import org.springframework.stereotype.Component

@Component
class DismissProcessedInvoicesNoticeUseCase(
    private val gateway: DismissProcessedInvoicesNoticeGateway,
) {
    fun execute(): Result<Unit> = gateway.execute()
}
