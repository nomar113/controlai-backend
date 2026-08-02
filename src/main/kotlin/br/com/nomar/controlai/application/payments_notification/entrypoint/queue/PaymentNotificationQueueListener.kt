package br.com.nomar.controlai.application.payments_notification.entrypoint.queue

import br.com.nomar.controlai.application.payments_notification.application.PaymentNotificationTextParser
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

@Component
class PaymentNotificationQueueListener(
    @Value("\${aws.sqs.payments-notifications-queue-url}")
    private val paymentsNotificationQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val paymentNotificationTextParser: PaymentNotificationTextParser,
    private val savePaymentNotificationUseCase: SavePaymentNotificationUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aws.sqs.listener.fixed-delay-ms:5000}")
    fun listen() {
        val messages = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(paymentsNotificationQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .build()
        ).messages()

        messages.forEach { message ->
            runCatching {
                val queueMessage = objectMapper.readValue(message.body(), PaymentNotificationQueueMessage::class.java)
                val paymentNotification = queueMessage.toPaymentNotification()
                savePaymentNotificationUseCase.execute(paymentNotification).getOrThrow()
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(paymentsNotificationQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
                )
            }.onFailure { ex ->
                if (ex is IllegalArgumentException && ex.message == "Payment notification limit reached for identical payload") {
                    sqsClient.deleteMessage(
                        DeleteMessageRequest.builder()
                            .queueUrl(paymentsNotificationQueueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
                    )
                    log.warn("Skipping duplicate payment notification after reaching limit. messageId={}", message.messageId())
                } else {
                    log.error("Failed to process message from payments-notification queue. messageId={}", message.messageId(), ex)
                }
            }
        }
    }

    private fun PaymentNotificationQueueMessage.toPaymentNotification(): PaymentNotification {
        // Messages produced before Task 3 have no groupId; fall back to the legacy group.
        val resolvedGroupId = groupId ?: LEGACY_GROUP_ID
        val rawText = text?.takeIf { it.isNotBlank() }
        if (rawText != null) {
            return paymentNotificationTextParser.parse(
                text = rawText,
                origin = origin,
                originType = originType,
            ).copy(groupId = resolvedGroupId)
        }

        return PaymentNotification(
            groupId = resolvedGroupId,
            cardLastDigits = requireNotNull(cardLastDigits) { "cardLastDigits is required when text is not provided" },
            purchasedAt = requireNotNull(purchasedAt) { "purchasedAt is required when text is not provided" },
            amount = requireNotNull(amount) { "amount is required when text is not provided" },
            merchantName = requireNotNull(merchantName) { "merchantName is required when text is not provided" },
            numberOfInstallments = numberOfInstallments ?: 1,
            currentInstallmentNumber = currentInstallmentNumber,
            origin = origin,
            originType = originType,
        )
    }

    companion object {
        private const val LEGACY_GROUP_ID = 1L
    }
}
