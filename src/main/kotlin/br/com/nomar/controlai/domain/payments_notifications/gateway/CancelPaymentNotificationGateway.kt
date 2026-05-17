package br.com.nomar.controlai.domain.payments_notifications.gateway

fun interface CancelPaymentNotificationGateway {
    fun execute(id: Long): Result<Unit>
}
