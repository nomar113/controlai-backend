package br.com.nomar.controlai.application.purchases_invoices.entrypoint.queue

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.usecase.SavePurchaseInvoiceUseCase
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest

@Component
class PurchaseInvoiceQueueListener(
    @Value("\${aws.sqs.purchases-invoices-queue-url}")
    private val purchaseInvoiceQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    private val savePurchaseInvoiceUseCase: SavePurchaseInvoiceUseCase,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelayString = "\${aws.sqs.listener.fixed-delay-ms:5000}")
    fun listen() {
        val messages = sqsClient.receiveMessage(
            ReceiveMessageRequest.builder()
                .queueUrl(purchaseInvoiceQueueUrl)
                .maxNumberOfMessages(10)
                .waitTimeSeconds(10)
                .build()
        ).messages()

        messages.forEach { message ->
            runCatching {
                val purchaseInvoice = objectMapper.readValue(message.body(), PurchaseInvoice::class.java)
                savePurchaseInvoiceUseCase.execute(purchaseInvoice).getOrThrow()
                sqsClient.deleteMessage(
                    DeleteMessageRequest.builder()
                        .queueUrl(purchaseInvoiceQueueUrl)
                        .receiptHandle(message.receiptHandle())
                        .build()
                )
            }.onFailure { ex ->
                if (ex is IllegalArgumentException && ex.message == "Purchase Invoice limit reached for identical payload") {
                    sqsClient.deleteMessage(
                        DeleteMessageRequest.builder()
                            .queueUrl(purchaseInvoiceQueueUrl)
                            .receiptHandle(message.receiptHandle())
                            .build()
                    )
                    log.warn("Skipping duplicate purchase Invoice after reaching limit. messageId={}", message.messageId())
                } else {
                    log.error("Failed to process message from purchases-invoices queue. messageId={}", message.messageId(), ex)
                }
            }
        }
    }
}