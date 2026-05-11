package br.com.nomar.controlai.application.payments_notification.entrypoint.rest

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.ManualPaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationTextRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateCategoryRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateCurrentInstallmentNumberRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateDescriptionRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
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
import java.time.YearMonth

@RestController
@RequestMapping("/payments")
class PaymentNotificationController(
    private val notificationQueueUseCase: NotifyPaymentNotificationQueueUseCase,
    private val deactivatePaymentNotificationUseCase: DeactivatePaymentNotificationUseCase,
    private val savePaymentNotificationUseCase: SavePaymentNotificationUseCase,
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val categoryRepository: CategoryRepository,
    private val budgetPeriodResolver: BudgetPeriodResolver,
) {

    @GetMapping("/notifications")
    fun listNotifications(
        @RequestParam month: String? = null,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "100") size: Int,
        @RequestParam categoryId: Long? = null,
    ): Map<String, Any> {
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
        val budgetId = budgetPeriodResolver.resolveBudgetId(yearMonth)
        val offset = page * size
        val items = paymentNotificationRepository.findByBudgetPeriods(budgetId, yearMonthStr, size, offset, categoryId)
            .map(PaymentNotificationResponse::from)
        val totalElements = paymentNotificationRepository.countByBudgetPeriods(budgetId, yearMonthStr, categoryId)
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
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }
        return PaymentNotificationResponse.from(notification)
    }

    @PatchMapping("/notifications/{id}/description")
    fun updateDescription(
        @PathVariable id: Long,
        @RequestBody request: UpdateDescriptionRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }
        val updated = paymentNotificationRepository.save(notification.copy(description = request.description))
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/category")
    fun updateCategory(
        @PathVariable id: Long,
        @RequestBody request: UpdateCategoryRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }

        val (categoryId, categoryName) = if (request.categoryId != null) {
            val category = categoryRepository.findById(request.categoryId)
                .orElseThrow { ResponseStatusException(HttpStatus.BAD_REQUEST, "Category not found") }
            category.id to category.name
        } else {
            null to null
        }

        val updated = paymentNotificationRepository.save(
            notification.copy(categoryId = categoryId, category = categoryName)
        )
        return PaymentNotificationResponse.from(updated)
    }

    @PatchMapping("/notifications/{id}/installment-number")
    fun updateCurrentInstallmentNumber(
        @PathVariable id: Long,
        @RequestBody request: UpdateCurrentInstallmentNumberRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }
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
        val paymentNotification = PaymentNotification(
            cardLastDigits = request.cardLastDigits,
            purchasedAt = request.purchasedAt,
            amount = request.amount,
            merchantName = request.merchantName,
            numberOfInstallments = request.numberOfInstallments,
            currentInstallmentNumber = request.currentInstallmentNumber,
            origin = "MANUAL",
            originType = "MANUAL",
            categoryId = request.categoryId,
            paymentMethodId = request.paymentMethodId,
            subCardId = request.subCardId,
        )
        val saved = savePaymentNotificationUseCase.execute(paymentNotification).getOrThrow()
        val response = PaymentNotificationResponse.from(saved)

        if (request.numberOfInstallments > 1) {
            val installments = if (request.installments != null) {
                require(request.installments.size == request.numberOfInstallments) {
                    "Installments size must match numberOfInstallments"
                }
                val sum = request.installments.sumOf { it.amount }
                require(sum.compareTo(request.amount) == 0) {
                    "Sum of installment amounts ($sum) must equal total amount (${request.amount})"
                }
                val amountsMap = request.installments.associate { it.installmentNumber to it.amount }
                createInstallmentsProvider.executeWithAmounts(
                    parentId = saved.id,
                    totalInstallments = request.numberOfInstallments,
                    amounts = amountsMap,
                    startDate = request.purchasedAt.toLocalDate(),
                )
            } else {
                createInstallmentsProvider.execute(
                    parentId = saved.id,
                    totalInstallments = request.numberOfInstallments,
                    totalAmount = request.amount,
                    startDate = request.purchasedAt.toLocalDate(),
                )
            }
            return response.copy(installments = installments.map(InstallmentResponse::from))
        }

        return response
    }
}
