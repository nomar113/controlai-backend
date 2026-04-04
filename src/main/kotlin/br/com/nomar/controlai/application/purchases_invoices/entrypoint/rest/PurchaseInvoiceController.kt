package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseResponse
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
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
    private val listPurchasesUseCase: ListPurchasesUseCase,
    private val purchaseRepository: PurchaseRepository,
) {

    @GetMapping
    fun listPurchases(): List<PurchaseResponse> =
        listPurchasesUseCase.execute().getOrThrow().map(PurchaseResponse::from)

    @GetMapping("/invoices")
    fun listInvoices(): List<PurchaseResponse> =
        purchaseRepository.findAllInvoices().map { projection ->
            PurchaseResponse.from(
                Purchase(
                    id = projection.getId(),
                    date = projection.getDate(),
                    total = projection.getTotal(),
                    merchantName = projection.getMerchantName(),
                    totalItems = projection.getTotalItems(),
                )
            )
        }

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePurchaseInvoice(@Validated @RequestBody request: PurchaseInvoiceRequest): PurchaseInvoiceRequest {
        notifyPurchaseInvoiceQueueUseCase.execute(request).getOrThrow()
        return request
    }
}
