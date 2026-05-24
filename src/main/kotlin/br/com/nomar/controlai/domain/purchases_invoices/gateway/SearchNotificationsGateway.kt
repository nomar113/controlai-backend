package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import java.math.BigDecimal
import java.time.LocalDateTime

fun interface SearchNotificationsGateway {
    fun execute(
        invoiceId: Long,
        amount: BigDecimal?,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
    ): Result<List<SuggestionResponse>>
}
