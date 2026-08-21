package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.YearMonth

private data class ReconciliationResult(val created: Int, val recalculated: Int)

/**
 * Full backfill for every non-cancelled/non-deleted purchase (installment or not) without rows in
 * `installments`, or with rows computed by the old due-date rule. Gated by a property instead of
 * running on every boot: this is a one-off migration, not boot infrastructure that every future
 * startup should pay for. Does not use RequestContext (request-scoped, unavailable at boot) nor its
 * own gateway/usecase.
 */
@Component
@ConditionalOnProperty(name = ["app.reconciliation.installments.run-full-backfill"], havingValue = "true")
class InstallmentReconciliationRunner(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val installmentRepository: InstallmentRepository,
    private val budgetPeriodResolver: BudgetPeriodResolver,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val ensureFutureBudgetGateway: EnsureFutureBudgetGateway,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(InstallmentReconciliationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        paymentNotificationRepository.findDistinctGroupIds().forEach { groupId ->
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
        return ReconciliationResult(created, recalculated)
    }

    private fun createInstallments(notification: PaymentNotification): Int {
        val installments = createInstallmentsProvider.execute(
            parentId = notification.id,
            groupId = notification.groupId,
            totalInstallments = notification.numberOfInstallments,
            totalAmount = notification.amount,
            startDate = notification.purchasedAt.toLocalDate(),
            paymentMethodId = notification.paymentMethodId,
        )
        ensureBudgetsFor(notification.groupId, installments.map { it.dueDate })
        return installments.size
    }

    private fun recalculateDueDates(notification: PaymentNotification, existing: List<Installment>): Int {
        val purchasedAt = notification.purchasedAt.toLocalDate()

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
