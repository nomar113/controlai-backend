package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SearchNotificationsGateway
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime

@Component
class SearchNotificationsUseCase(
    private val searchNotificationsGateway: SearchNotificationsGateway,
) {
    fun execute(
        invoiceId: Long,
        amount: BigDecimal?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Result<List<SuggestionResponse>> {
        return runCatching {
            searchNotificationsGateway.execute(invoiceId, amount, startDate, endDate).getOrThrow()
        }
    }
}
