package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.domain.payments_notifications.gateway.FindInvoiceSuggestionsGateway
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class FindInvoiceSuggestionsProvider(
    private val paymentNotificationRepository: PaymentNotificationRepository,
) : FindInvoiceSuggestionsGateway {

    override fun execute(amount: BigDecimal): Result<List<PaymentNotification>> {
        return runCatching {
            paymentNotificationRepository.findSuggestionsByAmount(amount = amount)
        }
    }
}
