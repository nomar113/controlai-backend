package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Full backfill for every group's non-cancelled/non-deleted purchases (installment or not) without
 * rows in `installments`, or with rows computed by the old due-date rule. Gated by a property instead
 * of running on every boot: this is a one-off migration, not boot infrastructure that every future
 * startup should pay for. Does not use RequestContext (request-scoped, unavailable at boot) nor its
 * own gateway/usecase.
 *
 * For a single-group, on-demand fix (e.g. right after correcting a card's period via
 * `PUT /budgets/{id}/periods`), use `POST /installments/reconcile` instead — see
 * [InstallmentReconciliationService], which backs both.
 */
@Component
@ConditionalOnProperty(name = ["app.reconciliation.installments.run-full-backfill"], havingValue = "true")
class InstallmentReconciliationRunner(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val reconciliationService: InstallmentReconciliationService,
) : ApplicationRunner {

    private val logger = LoggerFactory.getLogger(InstallmentReconciliationRunner::class.java)

    override fun run(args: ApplicationArguments) {
        paymentNotificationRepository.findDistinctGroupIds().forEach { groupId ->
            runCatching { reconciliationService.reconcileGroup(groupId) }
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
}
