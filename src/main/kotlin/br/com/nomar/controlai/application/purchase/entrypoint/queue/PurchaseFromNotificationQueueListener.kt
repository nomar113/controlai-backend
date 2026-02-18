package br.com.nomar.controlai.application.purchase.entrypoint.queue

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.domain.purchase.usecase.SavePurchaseFromNotificationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

@Component
class PurchaseFromNotificationQueueListener(
    @Value("\${aws.sqs.purchase-from-notification-queue-url}")
    private val purchaseFromNotificationQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val savePurchaseUseCase: SavePurchaseFromNotificationUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aws.sqs.listener.fixed-delay-ms:5000}")
    fun listen() {
        val messages = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(purchaseFromNotificationQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .build()
        ).messages()

        messages.forEach { message ->
            runCatching {
                val purchase = objectMapper.readValue(message.body(), Purchase::class.java)
                savePurchaseUseCase.execute(purchase).getOrThrow()
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(purchaseFromNotificationQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
                )
            }.onFailure { ex ->
                log.error("Failed to process message from purchase-from-notification queue. messageId={}", message.messageId(), ex)
            }
        }
    }
}
