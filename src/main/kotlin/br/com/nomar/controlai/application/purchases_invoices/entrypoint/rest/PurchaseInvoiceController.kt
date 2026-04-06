package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateDescriptionRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseInvoiceDetailResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseResponse
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/purchases")
class PurchaseInvoiceController(
    private val notifyPurchaseInvoiceQueueUseCase: NotifyPurchaseInvoiceQueueUseCase,
    private val listPurchasesUseCase: ListPurchasesUseCase,
    private val purchaseRepository: PurchaseRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val purchasePaymentRepository: PurchasePaymentRepository,
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
                    description = projection.getDescription(),
                )
            )
        }

    @GetMapping("/invoices/{id}")
    fun getInvoice(@PathVariable id: Long): PurchaseInvoiceDetailResponse {
        val invoice = purchaseInvoiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found") }
        val items = purchaseItemRepository.findByPurchaseInvoiceId(invoice.id!!)
        val payments = purchasePaymentRepository.findByPurchaseInvoiceId(invoice.id!!)
        return PurchaseInvoiceDetailResponse.from(invoice, items, payments)
    }

    @PatchMapping("/invoices/{id}/description")
    fun updateDescription(
        @PathVariable id: Long,
        @RequestBody request: UpdateDescriptionRequest,
    ): PurchaseInvoiceDetailResponse {
        val invoice = purchaseInvoiceRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found") }
        val updated = purchaseInvoiceRepository.save(invoice.copy(description = request.description))
        val items = purchaseItemRepository.findByPurchaseInvoiceId(updated.id!!)
        val payments = purchasePaymentRepository.findByPurchaseInvoiceId(updated.id!!)
        return PurchaseInvoiceDetailResponse.from(updated, items, payments)
    }

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePurchaseInvoice(@Validated @RequestBody request: PurchaseInvoiceRequest): PurchaseInvoiceRequest {
        notifyPurchaseInvoiceQueueUseCase.execute(request).getOrThrow()
        return request
    }
}
