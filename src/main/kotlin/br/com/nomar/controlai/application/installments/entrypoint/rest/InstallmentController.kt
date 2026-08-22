package br.com.nomar.controlai.application.installments.entrypoint.rest

import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.installments.application.InstallmentReconciliationService
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.installments.entrypoint.rest.request.InstallmentPreviewRequest
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentPreviewItemResponse
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.MonthlyProjectionResponse
import br.com.nomar.controlai.domain.auth.RequestContext
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
import java.time.LocalDate
import java.time.LocalDateTime

data class UpdateInstallmentRequest(
    val amount: BigDecimal? = null,
)

data class ReconcileInstallmentsResponse(
    val created: Int,
    val recalculated: Int,
)

@RestController
@RequestMapping("/installments")
class InstallmentController(
    private val installmentRepository: InstallmentRepository,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val reconciliationService: InstallmentReconciliationService,
    private val requestContext: RequestContext,
) {

    @PostMapping("/preview")
    fun previewInstallments(
        @Validated @RequestBody request: InstallmentPreviewRequest,
    ): List<InstallmentPreviewItemResponse> =
        // Must mirror SavePaymentNotificationProvider.createInstallments: without the real
        // card, the preview falls back to plain calendar months and can land the first
        // installment on a different month than what actually gets persisted.
        createInstallmentsProvider.calculate(
            totalInstallments = request.numberOfInstallments,
            totalAmount = request.totalAmount,
            startDate = request.startDate,
            groupId = requestContext.groupId,
            paymentMethodId = request.paymentMethodId,
        )

    @GetMapping
    fun listByParent(@RequestParam parentId: Long): List<InstallmentResponse> =
        installmentRepository.findByParentIdOrderByInstallmentNumber(parentId)
            .map(InstallmentResponse::from)

    @GetMapping("/projection")
    fun getProjection(): List<MonthlyProjectionResponse> {
        val startDate = LocalDate.now().withDayOfMonth(1)
        val endDate = startDate.plusMonths(6)
        return installmentRepository.getMonthlyProjection(requestContext.groupId, startDate, endDate)
            .map { MonthlyProjectionResponse(it.getYear(), it.getMonth(), it.getTotal(), it.getCount()) }
    }

    @PatchMapping("/{id}")
    fun updateInstallment(
        @PathVariable id: Long,
        @RequestBody request: UpdateInstallmentRequest,
    ): InstallmentResponse {
        val installment = installmentRepository.findByIdAndGroupId(id, requestContext.groupId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Installment not found")

        if (installment.dueDate.isBefore(LocalDate.now())) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot edit past installments")
        }

        val updated = installmentRepository.save(
            installment.copy(amount = request.amount ?: installment.amount)
        )
        return InstallmentResponse.from(updated)
    }

    @DeleteMapping("/future")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    fun cancelFutureInstallments(@RequestParam parentId: Long) {
        val installments = installmentRepository.findByParentIdAndCancelledAtIsNull(parentId)
        val now = LocalDate.now()
        val futureInstallments = installments.filter { it.dueDate.isAfter(now) }

        if (futureInstallments.isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "No future installments to cancel")
        }

        val cancelled = futureInstallments.map { it.copy(cancelledAt = LocalDateTime.now()) }
        installmentRepository.saveAll(cancelled)
    }

    // Run right after correcting a card's period (PUT /budgets/{id}/periods): that call only
    // overwrites budget_payment_periods, it never touches installments already persisted with
    // the old due_date. Scoped to the caller's own group via RequestContext.
    @PostMapping("/reconcile")
    @Transactional
    fun reconcile(): ReconcileInstallmentsResponse {
        val (created, recalculated) = reconciliationService.reconcileGroup(requestContext.groupId)
        return ReconcileInstallmentsResponse(created, recalculated)
    }
}
