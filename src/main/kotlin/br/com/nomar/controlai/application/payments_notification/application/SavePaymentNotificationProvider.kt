package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.interceptor.TransactionAspectSupport
import java.time.YearMonth

@Component
class SavePaymentNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val subCardRepository: SubCardRepository,
    private val createInstallmentsProvider: CreateInstallmentsProvider,
    private val ensureFutureBudgetGateway: EnsureFutureBudgetGateway,
): SavePaymentNotificationGateway {

    @Transactional
    override fun execute(paymentNotification: PaymentNotification): Result<PaymentNotification> {
        return runCatching {
            val digits = paymentNotification.cardLastDigits
            val enriched = enrichWithPaymentMethod(paymentNotification, digits)

            val existingCount = if (digits != null) {
                paymentNotificationRepository
                    .countByCardLastDigitsAndPurchasedAtAndAmountAndMerchantNameAndNumberOfInstallmentsAndOrigin(
                        cardLastDigits = digits,
                        purchasedAt = enriched.purchasedAt,
                        amount = enriched.amount,
                        merchantName = enriched.merchantName,
                        numberOfInstallments = enriched.numberOfInstallments,
                        origin = enriched.origin,
                    )
            } else {
                paymentNotificationRepository
                    .countByPaymentMethodIdAndPurchasedAtAndAmountAndMerchantNameAndNumberOfInstallmentsAndOrigin(
                        paymentMethodId = enriched.paymentMethodId,
                        purchasedAt = enriched.purchasedAt,
                        amount = enriched.amount,
                        merchantName = enriched.merchantName,
                        numberOfInstallments = enriched.numberOfInstallments,
                        origin = enriched.origin,
                    )
            }

            require(existingCount == 0L) {
                "Payment notification limit reached for identical payload"
            }

            val saved = paymentNotificationRepository.save(enriched)
            createInstallments(saved)
            saved
        }.onFailure {
            // runCatching swallows the exception into Result.failure, so it never reaches the
            // @Transactional proxy as a thrown RuntimeException; without this, a failure after
            // save() (e.g. installment/budget creation) would still commit the partial write.
            // This matters most for the SQS/SMS path, where this method is the only transaction
            // boundary (the manual endpoint also has its own @Transactional at the controller).
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly()
        }
    }

    private fun enrichWithPaymentMethod(
        paymentNotification: PaymentNotification,
        digits: String?,
    ): PaymentNotification {
        if (digits == null || paymentNotification.paymentMethodId != null) {
            return paymentNotification
        }

        val subCard = subCardRepository.findByLastFourDigits(digits) ?: return paymentNotification

        return paymentNotification.copy(
            paymentMethodId = subCard.paymentMethodId,
            subCardId = subCard.id,
        )
    }

    private fun createInstallments(notification: PaymentNotification) {
        val installments = createInstallmentsProvider.execute(
            parentId = notification.id,
            groupId = notification.groupId,
            totalInstallments = notification.numberOfInstallments,
            totalAmount = notification.amount,
            startDate = notification.purchasedAt.toLocalDate(),
            paymentMethodId = notification.paymentMethodId,
        )

        installments.map { YearMonth.from(it.dueDate) }.distinct().forEach { yearMonth ->
            ensureFutureBudgetGateway.execute(notification.groupId, yearMonth).getOrThrow()
        }
    }
}
