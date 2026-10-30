package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest

import br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.request.RegisterInvoiceReadFailureRequest
import br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.response.InvoiceReadFailureResponse
import br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.response.PendingInvoicesForProcessingResponse
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPendingInvoicesForProcessingUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.RegisterInvoiceReadFailureUseCase
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

// Endpoints of the NFC-e processor run by the operator on the Mac. Open without credential by the
// operator's decision (see CLAUDE.md, "NFC-e processor"): no group context, answers stay minimal
// and every write is conditional on the invoice still being PENDING with the same access key.
@RestController
@RequestMapping("/internal/nfce-processor")
class NfceProcessorController(
    private val listPendingInvoicesForProcessingUseCase: ListPendingInvoicesForProcessingUseCase,
    private val registerInvoiceReadFailureUseCase: RegisterInvoiceReadFailureUseCase,
) {

    // Constraints on the parameter trigger Spring's built-in method validation: out of range is 400
    @GetMapping("/invoices/pending")
    fun listPending(
        @RequestParam(defaultValue = "50") @Min(1) @Max(200) limit: Int,
    ): PendingInvoicesForProcessingResponse =
        listPendingInvoicesForProcessingUseCase.execute(limit)
            .map(PendingInvoicesForProcessingResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }

    @PostMapping("/invoices/{id}/failure")
    fun registerFailure(
        @PathVariable id: Long,
        @Validated @RequestBody request: RegisterInvoiceReadFailureRequest,
    ): InvoiceReadFailureResponse =
        registerInvoiceReadFailureUseCase.execute(id, request.accessKey, request.reason)
            .map(InvoiceReadFailureResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }
}
