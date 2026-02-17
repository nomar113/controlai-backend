package br.com.nomar.controlai.application.purchase.application

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Component
class PurchaseFromNotificationQueuePublisher(
    @Value("\${aws.sqs.purchase-from-notification-queue-url}")
    private val purchaseFromNotificationQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
) {

    fun publish(purchase: Purchase) {
        sqsClient.sendMessage(
            SendMessageRequest.builder()
                .queueUrl(purchaseFromNotificationQueueUrl)
                .messageBody(objectMapper.writeValueAsString(purchase))
                .build()
        )
    }
}
