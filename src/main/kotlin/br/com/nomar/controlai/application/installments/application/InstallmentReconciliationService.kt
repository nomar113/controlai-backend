package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

data class InstallmentReconciliationResult(val created: Int, val recalculated: Int)

/**
 * Reconciles a single group's installments against the currently persisted `budget_payment_periods`
 * (including any manual correction applied since the installments were created): creates missing
 * `installments` rows for purchases that have none yet, and recalculates `due_date` for purchases
 * whose installments already exist but no longer match what [BudgetPeriodResolver] would compute
 * today. Shared by [InstallmentReconciliationRunner] (full backfill, every group) and the on-demand
 * `/installments/reconcile` endpoint (single group, triggered right after a period correction).
 */
@Service
class InstallmentReconciliationService(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val installmentRepository: InstallmentRepository,
    private val budgetPeriodResolver: BudgetPeriodResolver,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val ensureFutureBudgetGateway: EnsureFutureBudgetGateway,
) {

    fun reconcileGroup(groupId: Long): InstallmentReconciliationResult {
        val notifications = paymentNotificationRepository
            .findByGroupIdAndCancelledAtIsNull(groupId)

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
        return InstallmentReconciliationResult(created, recalculated)
    }

    private fun createInstallments(notification: PaymentNotification): Int {
        val installments = createInstallmentsProvider.execute(
            parentId = notification.id,
            groupId = notification.groupId,
            totalInstallments = notification.numberOfInstallments,
            totalAmount = notification.amount,
            startDate = notification.purchasedAt.atZone(ZoneOffset.UTC).toLocalDate(),
            paymentMethodId = notification.paymentMethodId,
        )
        ensureBudgetsFor(notification.groupId, installments.map { it.dueDate })
        return installments.size
    }

    private fun recalculateDueDates(notification: PaymentNotification, existing: List<Installment>): Int {
        val purchasedAt = notification.purchasedAt.atZone(ZoneOffset.UTC).toLocalDate()

        val updated = existing.mapNotNull { installment ->
            val newDueDate = budgetPeriodResolver.resolveInstallmentDueDate(
                purchasedAt = purchasedAt,
                paymentMethodId = notification.paymentMethodId,
                installmentNumber = installment.installmentNumber,
                groupId = notification.groupId,
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
}
