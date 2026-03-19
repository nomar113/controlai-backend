package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.domain.payments_notifications.gateway.NotifyPaymentNotificationQueueGateway
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Component
class NotifyPaymentNotificationQueueProvider(
    @Value("\${aws.sqs.payments-notifications-queue-url}")
    private val paymentsNotificationQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
): NotifyPaymentNotificationQueueGateway {

    override fun execute(paymentNotification: PaymentNotificationQueueMessage): Result<Unit> {
        return runCatching {
            sqsClient.sendMessage(
                SendMessageRequest.builder()
                    .queueUrl(paymentsNotificationQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(paymentNotification))
                    .build()
            )
        }
    }
}
