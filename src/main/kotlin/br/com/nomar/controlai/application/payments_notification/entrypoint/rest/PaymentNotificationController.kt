package br.com.nomar.controlai.application.payments_notification.entrypoint.rest

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import br.com.nomar.controlai.application.payments_notification.application.AssociateNotificationProvider
import br.com.nomar.controlai.application.payments_notification.application.FindNotificationInvoiceSuggestionsProvider
import br.com.nomar.controlai.application.payments_notification.application.PaymentNotificationPeriodQueryProvider
import br.com.nomar.controlai.application.payments_notification.application.UpdateNotificationPaymentMethodProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.AssociateNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.InstallmentOverride
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.ManualPaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationTextRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateAmountRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateCategoryRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateCurrentInstallmentNumberRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateDescriptionRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdatePaymentMethodRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdatePurchasedAtRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.InvoiceSuggestionResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payments_notifications.usecase.CancelPaymentNotificationUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.DeactivatePaymentNotificationUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional
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
import java.time.YearMonth

@RestController
@RequestMapping("/payments")
class PaymentNotificationController(
    private val notificationQueueUseCase: NotifyPaymentNotificationQueueUseCase,
    private val deactivatePaymentNotificationUseCase: DeactivatePaymentNotificationUseCase,
    private val cancelPaymentNotificationUseCase: CancelPaymentNotificationUseCase,
    private val savePaymentNotificationUseCase: SavePaymentNotificationUseCase,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val installmentRepository: InstallmentRepository,
    private val categoryRepository: CategoryRepository,
    private val budgetPeriodResolver: BudgetPeriodResolver,
    private val paymentNotificationPeriodQueryProvider: PaymentNotificationPeriodQueryProvider,
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val findNotificationInvoiceSuggestionsProvider: FindNotificationInvoiceSuggestionsProvider,
    private val associateNotificationProvider: AssociateNotificationProvider,
    private val updateNotificationPaymentMethodProvider: UpdateNotificationPaymentMethodProvider,
    private val requestContext: RequestContext,
) {

    companion object {
        private val VALID_SORTS = setOf("recent", "amount")
    }

    private fun resolveCategory(categoryId: Long?, groupId: Long): CategoryModel? {
        if (categoryId == null) return null
        return categoryRepository.findByIdAndGroupId(categoryId, groupId)
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found")
    }

    @GetMapping("/notifications")
    fun listNotifications(
        @RequestParam month: String? = null,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam categoryId: Long? = null,
        @RequestParam cardLastDigits: String? = null,
        @RequestParam paymentMethodId: Long? = null,
        @RequestParam(defaultValue = "recent") sort: String,
    ): Map<String, Any> {
        if (sort !in VALID_SORTS) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid sort. Expected one of: ${VALID_SORTS.joinToString()}")
        }
        val yearMonth = if (month != null) {
            try {
                YearMonth.parse(month)
            } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format. Expected YYYY-MM")
            }
        } else {
            YearMonth.now()
        }
        val yearMonthStr = yearMonth.toString()
        val groupId = requestContext.groupId
        val periods = budgetPeriodResolver.resolvePeriods(yearMonth, groupId)
        val offset = page * size
        val notifications = paymentNotificationPeriodQueryProvider.findByBudgetPeriods(periods, yearMonthStr, groupId, size, offset, categoryId, cardLastDigits, paymentMethodId, sort)
        // The join in findByBudgetPeriods only picks the month's installment for filtering/sorting
        // and doesn't surface it in the pn.* projection, so it's fetched again here per notification.
        val installmentsByParentId = if (notifications.isEmpty()) {
            emptyMap()
        } else {
            installmentRepository
                .findByParentIdInAndCancelledAtIsNullAndDueDateBetween(notifications.map { it.id }, yearMonth.atDay(1), yearMonth.atEndOfMonth())
                .associateBy { it.parentId }
        }
        val items = notifications.map { PaymentNotificationResponse.from(it, installmentForMonth = installmentsByParentId[it.id]) }
        val totalElements = paymentNotificationPeriodQueryProvider.countByBudgetPeriods(periods, yearMonthStr, groupId, categoryId, cardLastDigits, paymentMethodId)
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

    @GetMapping("/notifications/{id}")
    fun getNotification(@PathVariable id: Long): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        val invoice = notification.purchaseInvoiceId
            ?.let { purchaseInvoiceRepository.findByIdAndGroupId(it, requestContext.groupId) }
        return PaymentNotificationResponse.from(notification, invoice)
    }

    @GetMapping("/notifications/{id}/invoice-suggestions")
    fun getInvoiceSuggestions(@PathVariable id: Long): List<InvoiceSuggestionResponse> {
        return findNotificationInvoiceSuggestionsProvider.execute(id).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

    @PatchMapping("/notifications/{id}/associate")
    fun associateInvoice(
        @PathVariable id: Long,
        @RequestBody request: AssociateNotificationRequest,
    ): PaymentNotificationResponse {
        return associateNotificationProvider.execute(id, request.purchaseInvoiceId).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

    @PatchMapping("/notifications/{id}/description")
    fun updateDescription(
        @PathVariable id: Long,
        @RequestBody request: UpdateDescriptionRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        val updated = paymentNotificationRepository.save(notification.copy(description = request.description))
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/amount")
    fun updateAmount(
        @PathVariable id: Long,
        @Validated @RequestBody request: UpdateAmountRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        if (notification.numberOfInstallments > 1) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Cannot edit amount of a purchase with installments")
        }
        val updated = paymentNotificationRepository.save(notification.copy(amount = request.amount))
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/category")
    fun updateCategory(
        @PathVariable id: Long,
        @RequestBody request: UpdateCategoryRequest,
    ): PaymentNotificationResponse {
        val groupId = requestContext.groupId
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")

        val category = resolveCategory(request.categoryId, groupId)
        val updated = paymentNotificationRepository.save(notification.copy(category = category))
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/purchased-at")
    fun updatePurchasedAt(
        @PathVariable id: Long,
        @Validated @RequestBody request: UpdatePurchasedAtRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        val updated = paymentNotificationRepository.save(notification.copy(purchasedAt = request.purchasedAt))
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/payment-method")
    fun updatePaymentMethod(
        @PathVariable id: Long,
        @Validated @RequestBody request: UpdatePaymentMethodRequest,
    ): PaymentNotificationResponse {
        val notification = updateNotificationPaymentMethodProvider
            .execute(id, request.paymentMethodId, request.subCardId)
            .getOrElse { ex ->
                when (ex) {
                    is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                    is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    is IllegalArgumentException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
        return PaymentNotificationResponse.from(notification)
    }

    @PatchMapping("/notifications/{id}/installment-number")
    fun updateCurrentInstallmentNumber(
        @PathVariable id: Long,
        @RequestBody request: UpdateCurrentInstallmentNumberRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found")
        val totalInstallments = request.numberOfInstallments ?: notification.numberOfInstallments
        if (request.currentInstallmentNumber != null && request.currentInstallmentNumber > totalInstallments) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "currentInstallmentNumber cannot exceed numberOfInstallments")
        }
        val updated = paymentNotificationRepository.save(
            notification.copy(
                numberOfInstallments = totalInstallments,
                currentInstallmentNumber = request.currentInstallmentNumber,
            )
        )
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/cancel")
    @ResponseStatus(HttpStatus.OK)
    fun cancelNotification(@PathVariable id: Long) {
        cancelPaymentNotificationUseCase.execute(id).getOrElse { ex ->
            when (ex) {
                is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }

    @DeleteMapping("/notifications/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNotification(@PathVariable id: Long) {
        deactivatePaymentNotificationUseCase.execute(id)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PostMapping("/notification")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePaymentNotification(@Validated @RequestBody request: PaymentNotificationTextRequest): Map<String, Any> {
        val queueMessage = PaymentNotificationQueueMessage(
            text = request.text,
            origin = request.origin,
            originType = request.originType ?: "HTTP_REQUEST",
            groupId = requestContext.groupId,
        )
        notificationQueueUseCase.execute(queueMessage).getOrThrow()
        return mapOf(
            "text" to queueMessage.text.orEmpty(),
            "origin" to queueMessage.origin,
            "originType" to queueMessage.originType,
        )
    }

    @PostMapping("/notifications/manual")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    fun createManualNotification(@Validated @RequestBody request: ManualPaymentNotificationRequest): PaymentNotificationResponse {
        val category = resolveCategory(request.categoryId, requestContext.groupId)
        val paymentNotification = PaymentNotification(
            groupId = requestContext.groupId,
            cardLastDigits = request.cardLastDigits,
            purchasedAt = request.purchasedAt,
            amount = request.amount,
            merchantName = request.merchantName,
            numberOfInstallments = request.numberOfInstallments,
            currentInstallmentNumber = request.currentInstallmentNumber,
            origin = "MANUAL",
            originType = "MANUAL",
            category = category,
            paymentMethodId = request.paymentMethodId,
            subCardId = request.subCardId,
        )
        val saved = savePaymentNotificationUseCase.execute(paymentNotification).getOrThrow()
        val response = PaymentNotificationResponse.from(saved)

        if (saved.numberOfInstallments > 1) {
            val installments = if (request.installments != null) {
                applyInstallmentOverrides(saved.id, request.numberOfInstallments, request.amount, request.installments)
            } else {
                installmentRepository.findByParentIdOrderByInstallmentNumber(saved.id)
            }
            return response.copy(installments = installments.map(InstallmentResponse::from))
        }

        return response
    }

    // SavePaymentNotificationProvider already created the default split; overrides only adjust
    // the amount of each already-created installment, never a second set of rows.
    private fun applyInstallmentOverrides(
        parentId: Long,
        numberOfInstallments: Int,
        totalAmount: BigDecimal,
        overrides: List<InstallmentOverride>,
    ): List<Installment> {
        require(overrides.size == numberOfInstallments) {
            "Installments size must match numberOfInstallments"
        }
        val sum = overrides.sumOf { it.amount }
        require(sum.compareTo(totalAmount) == 0) {
            "Sum of installment amounts ($sum) must equal total amount ($totalAmount)"
        }

        val existingByNumber = installmentRepository.findByParentIdOrderByInstallmentNumber(parentId)
            .associateBy { it.installmentNumber }
        val adjusted = overrides.map { override ->
            val installment = existingByNumber[override.installmentNumber]
                ?: throw IllegalStateException("Installment ${override.installmentNumber} not found for parent $parentId")
            installment.copy(amount = override.amount)
        }
        return installmentRepository.saveAll(adjusted)
    }
}
