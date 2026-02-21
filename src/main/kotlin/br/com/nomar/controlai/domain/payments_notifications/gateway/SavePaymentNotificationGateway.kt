package br.com.nomar.controlai.domain.payments_notifications.gateway

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification

fun interface SavePaymentNotificationGateway {
    fun execute(
        paymentNotification: PaymentNotification,
    ): Result<PaymentNotification>
}
