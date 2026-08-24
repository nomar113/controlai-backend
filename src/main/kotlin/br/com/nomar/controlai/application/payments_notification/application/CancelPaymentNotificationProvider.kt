package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.payments_notifications.gateway.CancelPaymentNotificationGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.time.ZoneOffset

@Component
class CancelPaymentNotificationProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
    private val requestContext: RequestContext,
) : CancelPaymentNotificationGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = paymentNotificationRepository.findByIdAndGroupId(id, requestContext.groupId)
                ?: throw NoSuchElementException("PaymentNotification not found: $id")

            if (model.cancelledAt != null) {
                throw IllegalStateException("PaymentNotification already cancelled: $id")
            }

            // cancelledAt is still LocalDateTime (not Instant — see PaymentNotificationResponse), so it
            // must be pinned to UTC explicitly here: hibernate.jdbc.time_zone=UTC only controls how the
            // value is bound/read against the DB session (already forced to UTC), not what JVM-local
            // clock LocalDateTime.now() would otherwise capture.
            paymentNotificationRepository.save(model.copy(cancelledAt = LocalDateTime.now(ZoneOffset.UTC)))
        }
    }
}
