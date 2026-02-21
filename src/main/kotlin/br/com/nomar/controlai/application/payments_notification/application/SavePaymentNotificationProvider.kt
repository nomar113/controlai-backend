package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import org.springframework.stereotype.Component

@Component
class SavePaymentNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
): SavePaymentNotificationGateway {

    override fun execute(paymentNotification: PaymentNotification): Result<PaymentNotification> {
        return runCatching {
            val existingCount = paymentNotificationRepository
                .countByCardLastDigitsAndPurchasedAtAndAmountAndMerchantNameAndNumberOfInstallmentsAndOrigin(
                    cardLastDigits = paymentNotification.cardLastDigits,
                    purchasedAt = paymentNotification.purchasedAt,
                    amount = paymentNotification.amount,
                    merchantName = paymentNotification.merchantName,
                    numberOfInstallments = paymentNotification.numberOfInstallments,
                    origin = paymentNotification.origin,
                )

            require(existingCount < 3) {
                "Payment notification limit reached for identical payload"
            }

            paymentNotificationRepository.save(paymentNotification)
        }
    }
}
