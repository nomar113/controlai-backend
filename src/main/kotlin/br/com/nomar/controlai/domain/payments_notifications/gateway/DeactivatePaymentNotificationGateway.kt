package br.com.nomar.controlai.domain.payments_notifications.gateway

fun interface DeactivatePaymentNotificationGateway {
    fun execute(id: Long): Result<Unit>
}
