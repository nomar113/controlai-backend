package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.CancelPaymentNotificationGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class CancelPaymentNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
) : CancelPaymentNotificationGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = paymentNotificationRepository.findById(id)
                .orElseThrow { NoSuchElementException("PaymentNotification not found: $id") }

            if (model.cancelledAt != null) {
                throw IllegalStateException("PaymentNotification already cancelled: $id")
            }

            paymentNotificationRepository.save(model.copy(cancelledAt = LocalDateTime.now()))
        }
    }
}
