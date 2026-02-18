package br.com.nomar.controlai.application.purchase.application

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.domain.purchase.gateway.NotifyPurchaseFromNotificationQueueGateway
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Component
class NotifyPurchaseFromNotificationQueueProvider(
    @Value("\${aws.sqs.purchase-from-notification-queue-url}")
    private val purchaseFromNotificationQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
): NotifyPurchaseFromNotificationQueueGateway {

    override fun execute(purchase: Purchase): Result<Unit> {
        return runCatching {
            sqsClient.sendMessage(
                SendMessageRequest.builder()
                    .queueUrl(purchaseFromNotificationQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(purchase))
                    .build()
            )
        }
    }
}
