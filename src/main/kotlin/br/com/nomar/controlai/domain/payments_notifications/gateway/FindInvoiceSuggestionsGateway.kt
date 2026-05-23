package br.com.nomar.controlai.domain.payments_notifications.gateway

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import java.math.BigDecimal
import java.time.LocalDateTime

fun interface FindInvoiceSuggestionsGateway {
    fun execute(
        amount: BigDecimal,
        startDate: LocalDateTime,
        endDate: LocalDateTime,
        invoiceDate: LocalDateTime,
    ): Result<List<PaymentNotification>>
}
