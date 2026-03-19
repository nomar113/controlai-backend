package br.com.nomar.controlai.application.payments_notification.entrypoint.queue

import br.com.nomar.controlai.application.payments_notification.application.PaymentNotificationTextParser
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse
import software.amazon.awssdk.services.sqs.model.Message
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse
import kotlin.test.assertEquals

class PaymentNotificationQueueListenerTest {

    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Test
    fun `should parse raw notification text from queue before saving`() {
        val sqsClient = mock(SqsClient::class.java)
        var savedNotification: PaymentNotification? = null
        val saveUseCase = SavePaymentNotificationUseCase(
            SavePaymentNotificationGateway {
                savedNotification = it
                Result.success(it.copy(id = 1))
            }
        )
        val listener = PaymentNotificationQueueListener(
            paymentsNotificationQueueUrl = "queue-url",
            sqsClient = sqsClient,
            objectMapper = objectMapper,
            paymentNotificationTextParser = PaymentNotificationTextParser(),
            savePaymentNotificationUseCase = saveUseCase,
        )
        val payload = PaymentNotificationQueueMessage(
            text = "Compra aprovada no cartao final 1234 em 18/03/2026 10:45. Valor de R$ 99,90 EM 3x Mercado Teste.",
            origin = "sms-app",
            originType = "sms",
        )
        val message = Message.builder()
            .messageId("message-1")
            .receiptHandle("receipt-1")
            .body(objectMapper.writeValueAsString(payload))
            .build()

        `when`(sqsClient.receiveMessage(any(ReceiveMessageRequest::class.java))).thenReturn(
            ReceiveMessageResponse.builder()
                .messages(message)
                .build()
        )
        `when`(sqsClient.deleteMessage(any(DeleteMessageRequest::class.java))).thenReturn(DeleteMessageResponse.builder().build())

        listener.listen()

        verify(sqsClient, times(1)).deleteMessage(any(DeleteMessageRequest::class.java))
        assertEquals("1234", savedNotification?.cardLastDigits)
        assertEquals("Mercado Teste", savedNotification?.merchantName)
        assertEquals(3, savedNotification?.numberOfInstallments)
        assertEquals("sms-app", savedNotification?.origin)
        assertEquals("SMS", savedNotification?.originType)
    }

    @Test
    fun `should keep supporting structured queue messages`() {
        val sqsClient = mock(SqsClient::class.java)
        var savedNotification: PaymentNotification? = null
        val saveUseCase = SavePaymentNotificationUseCase(
            SavePaymentNotificationGateway {
                savedNotification = it
                Result.success(it.copy(id = 1))
            }
        )
        val listener = PaymentNotificationQueueListener(
            paymentsNotificationQueueUrl = "queue-url",
            sqsClient = sqsClient,
            objectMapper = objectMapper,
            paymentNotificationTextParser = PaymentNotificationTextParser(),
            savePaymentNotificationUseCase = saveUseCase,
        )
        val payload = PaymentNotificationQueueMessage(
            cardLastDigits = "5678",
            purchasedAt = java.time.LocalDateTime.of(2026, 3, 18, 11, 0),
            amount = java.math.BigDecimal("10.00"),
            merchantName = "Loja Teste",
            numberOfInstallments = 1,
            origin = "HTTP_REQUEST",
            originType = "HTTP_REQUEST",
        )
        val message = Message.builder()
            .messageId("message-2")
            .receiptHandle("receipt-2")
            .body(objectMapper.writeValueAsString(payload))
            .build()

        `when`(sqsClient.receiveMessage(any(ReceiveMessageRequest::class.java))).thenReturn(
            ReceiveMessageResponse.builder()
                .messages(message)
                .build()
        )
        `when`(sqsClient.deleteMessage(any(DeleteMessageRequest::class.java))).thenReturn(DeleteMessageResponse.builder().build())

        listener.listen()

        assertEquals("5678", savedNotification?.cardLastDigits)
        assertEquals("Loja Teste", savedNotification?.merchantName)
        assertEquals("HTTP_REQUEST", savedNotification?.originType)
    }
}
