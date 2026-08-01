package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateDescriptionRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.AssociateInvoiceRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.AssociateInvoiceResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseInvoiceDetailResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseResponse
import br.com.nomar.controlai.application.suggestion.entrypoint.rest.response.SuggestionResponse
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.AssociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.CancelPurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DeactivatePurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DisassociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.usecase.SearchNotificationsUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

@RestController
@RequestMapping("/purchases")
class PurchaseInvoiceController(
    private val notifyPurchaseInvoiceQueueUseCase: NotifyPurchaseInvoiceQueueUseCase,
    private val cancelPurchaseInvoiceUseCase: CancelPurchaseInvoiceUseCase,
    private val deactivatePurchaseInvoiceUseCase: DeactivatePurchaseInvoiceUseCase,
    private val listPurchasesUseCase: ListPurchasesUseCase,
    private val associateInvoiceUseCase: AssociateInvoiceUseCase,
    private val disassociateInvoiceUseCase: DisassociateInvoiceUseCase,
    private val searchNotificationsUseCase: SearchNotificationsUseCase,
    private val purchaseRepository: PurchaseRepository,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val purchaseItemRepository: PurchaseItemRepository,
    private val purchasePaymentRepository: PurchasePaymentRepository,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val requestContext: RequestContext,
) {

    @GetMapping
    fun listPurchases(): List<PurchaseResponse> =
        listPurchasesUseCase.execute().getOrThrow().map(PurchaseResponse::from)

    @GetMapping("/invoices")
    fun listInvoices(
        @RequestParam month: String? = null,
        @RequestParam startDate: String? = null,
        @RequestParam endDate: String? = null,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "50") size: Int,
        @RequestParam categoryId: Long? = null,
    ): Map<String, Any> {
        val (resolvedStart, resolvedEnd) = resolveDateRange(month, startDate, endDate)
        val groupId = requestContext.groupId
        val offset = page * size
        val items = purchaseRepository.findInvoicesByDateRange(groupId, resolvedStart, resolvedEnd, size, offset, categoryId)
            .map { projection ->
                PurchaseResponse.from(
                    Purchase(
                        id = projection.getId(),
                        date = projection.getDate(),
                        total = projection.getTotal(),
                        merchantName = projection.getMerchantName(),
                        totalItems = projection.getTotalItems(),
                        description = projection.getDescription(),
                        categoryName = projection.getCategoryName(),
                        categoryId = projection.getCategoryId(),
                        cancelledAt = projection.getCancelledAt(),
                    )
                )
            }
        val totalElements = purchaseRepository.countInvoicesByDateRange(groupId, resolvedStart, resolvedEnd, categoryId)
        val totalPages = if (size > 0) ((totalElements + size - 1) / size).toInt() else 0
        return mapOf(
            "content" to items,
            "totalElements" to totalElements,
            "totalPages" to totalPages,
            "number" to page,
            "size" to size,
            "last" to (page >= totalPages - 1),
        )
    }

    private fun resolveDateRange(month: String?, startDate: String?, endDate: String?): Pair<String, String> {
        if (startDate != null && endDate != null) {
            val start = try { LocalDate.parse(startDate) } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid startDate format. Expected YYYY-MM-DD")
            }
            val end = try { LocalDate.parse(endDate) } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid endDate format. Expected YYYY-MM-DD")
            }
            if (start.isAfter(end)) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate")
            }
            return start.atStartOfDay().toString() to end.plusDays(1).atStartOfDay().toString()
        }
        val yearMonth = if (month != null) {
            try { YearMonth.parse(month) } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format. Expected YYYY-MM")
            }
        } else {
            YearMonth.now()
        }
        return yearMonth.atDay(1).atStartOfDay().toString() to yearMonth.plusMonths(1).atDay(1).atStartOfDay().toString()
    }

    @GetMapping("/invoices/{id}")
    fun getInvoice(@PathVariable id: Long): PurchaseInvoiceDetailResponse {
        val invoice = purchaseInvoiceRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")
        val items = purchaseItemRepository.findByPurchaseInvoiceId(invoice.id!!)
        val payments = purchasePaymentRepository.findByPurchaseInvoiceId(invoice.id!!)
        val associatedPayment = paymentNotificationRepository.findByPurchaseInvoiceId(invoice.id!!)
        return PurchaseInvoiceDetailResponse.from(invoice, items, payments, associatedPayment)
    }

    @PatchMapping("/invoices/{id}/description")
    fun updateDescription(
        @PathVariable id: Long,
        @RequestBody request: UpdateDescriptionRequest,
    ): PurchaseInvoiceDetailResponse {
        val invoice = purchaseInvoiceRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found")
        val updated = purchaseInvoiceRepository.save(invoice.copy(description = request.description))
        val items = purchaseItemRepository.findByPurchaseInvoiceId(updated.id!!)
        val payments = purchasePaymentRepository.findByPurchaseInvoiceId(updated.id!!)
        val associatedPayment = paymentNotificationRepository.findByPurchaseInvoiceId(updated.id!!)
        return PurchaseInvoiceDetailResponse.from(updated, items, payments, associatedPayment)
    }

    @PatchMapping("/invoices/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelInvoice(@PathVariable id: Long) {
        cancelPurchaseInvoiceUseCase.execute(id).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

    @DeleteMapping("/invoices/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteInvoice(@PathVariable id: Long) {
        deactivatePurchaseInvoiceUseCase.execute(id)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PostMapping("/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePurchaseInvoice(@Validated @RequestBody request: PurchaseInvoiceRequest): PurchaseInvoiceRequest {
        notifyPurchaseInvoiceQueueUseCase.execute(request).getOrThrow()
        return request
    }

    @PatchMapping("/invoices/{invoiceId}/associate")
    fun associateInvoice(
        @PathVariable invoiceId: Long,
        @RequestBody request: AssociateInvoiceRequest,
    ): AssociateInvoiceResponse {
        return associateInvoiceUseCase.execute(invoiceId, request.paymentNotificationId).getOrElse { ex ->
            throw mapAssociationError(ex)
        }
    }

    @DeleteMapping("/invoices/{invoiceId}/associate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun disassociateInvoice(@PathVariable invoiceId: Long) {
        disassociateInvoiceUseCase.execute(invoiceId).getOrElse { ex ->
            throw mapAssociationError(ex)
        }
    }

    @GetMapping("/invoices/{invoiceId}/suggestions/search")
    fun searchSuggestions(
        @PathVariable invoiceId: Long,
        @RequestParam(required = false) amount: BigDecimal? = null,
        @RequestParam(required = false) startDate: String? = null,
        @RequestParam(required = false) endDate: String? = null,
    ): List<SuggestionResponse> {
        val parsedStart = parseDateTimeParam("startDate", startDate)
        val parsedEnd = parseDateTimeParam("endDate", endDate)

        return searchNotificationsUseCase.execute(invoiceId, amount, parsedStart, parsedEnd).getOrElse { ex ->
            throw mapAssociationError(ex)
        }
    }

    private fun parseDateTimeParam(name: String, value: String?): LocalDateTime? {
        if (value.isNullOrBlank()) return null
        return try {
            LocalDateTime.parse(value)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid $name format. Expected ISO LocalDateTime (yyyy-MM-ddTHH:mm:ss)")
        }
    }

    private fun mapAssociationError(ex: Throwable): ResponseStatusException = when (ex) {
        is NoSuchElementException -> ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
        is IllegalStateException -> ResponseStatusException(HttpStatus.CONFLICT, ex.message)
        is IllegalArgumentException -> ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
        else -> ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
    }
}
