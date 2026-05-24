package br.com.nomar.controlai.domain.payments_notifications.gateway

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import java.math.BigDecimal

fun interface FindInvoiceSuggestionsGateway {
    fun execute(amount: BigDecimal): Result<List<PaymentNotification>>
}
