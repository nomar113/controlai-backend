package br.com.nomar.controlai.application.suggestion.entrypoint.rest

import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import br.com.nomar.controlai.domain.payments_notifications.usecase.FindInvoiceSuggestionsUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/purchases/invoices")
class SuggestionController(
    private val findInvoiceSuggestionsUseCase: FindInvoiceSuggestionsUseCase,
) {

    @GetMapping("/{id}/suggestions")
    fun getSuggestions(@PathVariable id: Long): List<SuggestionResponse> {
        val result = findInvoiceSuggestionsUseCase.execute(id).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }

        return result.notifications.map { SuggestionResponse.from(it, result.invoiceDate) }
    }
}
