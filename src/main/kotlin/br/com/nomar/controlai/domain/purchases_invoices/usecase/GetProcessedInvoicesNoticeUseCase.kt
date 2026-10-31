package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.domain.purchases_invoices.gateway.GetProcessedInvoicesNoticeGateway
import org.springframework.stereotype.Component

@Component
class GetProcessedInvoicesNoticeUseCase(
    private val gateway: GetProcessedInvoicesNoticeGateway,
) {
    fun execute(): Result<Int> = gateway.execute()
}
