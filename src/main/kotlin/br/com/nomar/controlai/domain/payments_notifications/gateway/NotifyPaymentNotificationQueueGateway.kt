package br.com.nomar.controlai.domain.payments_notifications.gateway

import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage

fun interface NotifyPaymentNotificationQueueGateway {
    fun execute(
        paymentNotification: PaymentNotificationQueueMessage,
    ): Result<Unit>
}
