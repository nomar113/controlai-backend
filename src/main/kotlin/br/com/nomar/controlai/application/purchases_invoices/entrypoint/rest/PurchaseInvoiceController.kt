package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseInvoiceResponse
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchaseInvoicesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/purchases")
class PurchaseInvoiceController(
    private val notifyPurchaseInvoiceQueueUseCase: NotifyPurchaseInvoiceQueueUseCase,
    private val listPurchaseInvoicesUseCase: ListPurchaseInvoicesUseCase,
) {

    @GetMapping
    fun listPurchases(): List<PurchaseInvoiceResponse> =
        listPurchaseInvoicesUseCase.execute().getOrThrow().map(PurchaseInvoiceResponse::from)

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePurchaseInvoice(@Validated @RequestBody request: PurchaseInvoiceRequest): PurchaseInvoiceRequest {
        notifyPurchaseInvoiceQueueUseCase.execute(request).getOrThrow()
        return request
    }
}
