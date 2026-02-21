package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.SendMessageRequest

@Component
class NotifyPurchaseInvoiceQueueProvider(
    @Value("\${aws.sqs.purchases-invoices-queue-url}")
    private val purchasesInvoicesQueueUrl: String,
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
) : NotifyPurchaseInvoiceQueueGateway {

    override fun execute(purchaseInvoice: PurchaseInvoiceRequest): Result<Unit> {
        return runCatching {
            sqsClient.sendMessage(
                SendMessageRequest.builder()
                    .queueUrl(purchasesInvoicesQueueUrl)
                    .messageBody(objectMapper.writeValueAsString(purchaseInvoice))
                    .build()
            )
        }
    }
}
