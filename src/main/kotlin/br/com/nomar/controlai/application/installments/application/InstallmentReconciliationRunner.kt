package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodCalculator
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

private data class ReconciliationResult(val created: Int, val recalculated: Int)

/**
 * On boot, recalculates installments for purchases already registered (with or without rows in
 * `installments`) for the new invoice-closing model. Does not use RequestContext (request-scoped,
 * unavailable at boot) nor its own gateway/usecase: this is boot infrastructure, not a domain operation.
 */
@Component
class InstallmentReconciliationRunner(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val installmentRepository: InstallmentRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val periodCalculator: BudgetPeriodCalculator,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val ensureFutureBudgetGateway: EnsureFutureBudgetGateway,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(InstallmentReconciliationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        paymentNotificationRepository.findDistinctGroupIdsWithInstallmentPurchases().forEach { groupId ->
            runCatching { reconcileGroup(groupId) }
                .onSuccess { (created, recalculated) ->
                    logger.info(
                        "Installment reconciliation for group $groupId: $created installments created, $recalculated due dates recalculated"
                    )
                }
                .onFailure { ex ->
                    logger.warn(
                        "Installment reconciliation failed for group $groupId, skipping remaining purchases in this group",
                        ex,
                    )
                }
        }
    }

    private fun reconcileGroup(groupId: Long): ReconciliationResult {
        val notifications = paymentNotificationRepository
            .findByGroupIdAndNumberOfInstallmentsGreaterThanAndCancelledAtIsNull(groupId, 1)

        var created = 0
        var recalculated = 0
        notifications.forEach { notification ->
            val existing = installmentRepository.findByParentIdOrderByInstallmentNumber(notification.id)
            if (existing.isEmpty()) {
                created += createInstallments(notification)
            } else {
                recalculated += recalculateDueDates(notification, existing)
            }
        }
        return ReconciliationResult(created, recalculated)
    }

    private fun createInstallments(notification: PaymentNotification): Int {
        val paymentMethod = resolvePaymentMethod(notification)
        val installments = createInstallmentsProvider.execute(
            parentId = notification.id,
            groupId = notification.groupId,
            totalInstallments = notification.numberOfInstallments,
            totalAmount = notification.amount,
            startDate = notification.purchasedAt.toLocalDate(),
            closingDay = paymentMethod?.closingDay,
            type = paymentMethod?.type ?: "OTHER",
        )
        ensureBudgetsFor(notification.groupId, installments.map { it.dueDate })
        return installments.size
    }

    private fun recalculateDueDates(notification: PaymentNotification, existing: List<Installment>): Int {
        val paymentMethod = resolvePaymentMethod(notification)
        val purchasedAt = notification.purchasedAt.toLocalDate()

        val updated = existing.mapNotNull { installment ->
            val newDueDate = periodCalculator.resolveInstallmentDueDate(
                purchasedAt = purchasedAt,
                closingDay = paymentMethod?.closingDay,
                type = paymentMethod?.type ?: "OTHER",
                installmentNumber = installment.installmentNumber,
            )
            if (newDueDate != installment.dueDate) installment.copy(dueDate = newDueDate) else null
        }

        if (updated.isNotEmpty()) {
            installmentRepository.saveAll(updated)
            ensureBudgetsFor(notification.groupId, updated.map { it.dueDate })
        }
        return updated.size
    }

    private fun ensureBudgetsFor(groupId: Long, dueDates: List<LocalDate>) {
        dueDates.map { YearMonth.from(it) }.distinct().forEach { yearMonth ->
            ensureFutureBudgetGateway.execute(groupId, yearMonth).getOrThrow()
        }
    }

    private fun resolvePaymentMethod(notification: PaymentNotification) =
        notification.paymentMethodId?.let { paymentMethodRepository.findByIdAndGroupId(it, notification.groupId) }
}
