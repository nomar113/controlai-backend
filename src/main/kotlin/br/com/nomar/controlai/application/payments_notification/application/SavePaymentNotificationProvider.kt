package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import org.springframework.stereotype.Component

@Component
class SavePaymentNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val subCardRepository: SubCardRepository,
): SavePaymentNotificationGateway {

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

            require(existingCount < 3) {
                "Payment notification limit reached for identical payload"
            }

            paymentNotificationRepository.save(enriched)
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
}
