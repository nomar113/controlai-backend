package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class UpdateNotificationPaymentMethodProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
) {

    private val logger = LoggerFactory.getLogger(UpdateNotificationPaymentMethodProvider::class.java)

    @Transactional
    fun execute(
        notificationId: Long,
        paymentMethodId: Long,
        subCardId: Long?,
    ): Result<PaymentNotification> {
        return runCatching {
            val notification = paymentNotificationRepository.findById(notificationId)
                .orElseThrow { NoSuchElementException("PaymentNotification not found: $notificationId") }

            if (notification.cancelledAt != null) {
                throw IllegalStateException("PaymentNotification is cancelled: $notificationId")
            }

            val paymentMethod = paymentMethodRepository.findById(paymentMethodId)
                .orElseThrow { IllegalArgumentException("PaymentMethod not found: $paymentMethodId") }

            val subCard = subCardId?.let { id ->
                paymentMethod.subCards.firstOrNull { it.id == id }
                    ?: throw IllegalArgumentException(
                        "SubCard $id does not belong to PaymentMethod $paymentMethodId"
                    )
            }

            val newCardLastDigits = subCard?.lastFourDigits ?: notification.cardLastDigits

            val updated = paymentNotificationRepository.save(
                notification.copy(
                    paymentMethodId = paymentMethodId,
                    subCardId = subCardId,
                    cardLastDigits = newCardLastDigits,
                )
            )

            logger.info(
                "PaymentNotification {} updated: oldPaymentMethodId={}, newPaymentMethodId={}, subCardChanged={}",
                notificationId,
                notification.paymentMethodId,
                paymentMethodId,
                notification.subCardId != subCardId,
            )

            updated
        }
    }
}
