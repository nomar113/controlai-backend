package br.com.nomar.controlai.domain.usecase

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceItemRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoicePaymentRequest
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.PurchaseInvoiceRequest
import br.com.nomar.controlai.domain.payments_notifications.gateway.NotifyPaymentNotificationQueueGateway
import br.com.nomar.controlai.domain.payments_notifications.gateway.SavePaymentNotificationGateway
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SavePurchaseInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.SavePurchaseInvoiceUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

class UseCasesTest {

    @Test
    fun `save payment notification use case should return success`() {
        val notification = sampleNotification()
        val gateway = SavePaymentNotificationGateway { Result.success(it.copy(id = 10)) }
        val useCase = SavePaymentNotificationUseCase(gateway)

        val result = useCase.execute(notification)

        assertTrue(result.isSuccess)
        assertEquals(10, result.getOrNull()?.id)
    }

    @Test
    fun `notify payment notification queue use case should return failure`() {
        val gateway = NotifyPaymentNotificationQueueGateway {
            Result.failure(IllegalStateException("queue unavailable"))
        }
        val useCase = NotifyPaymentNotificationQueueUseCase(gateway)

        val result = useCase.execute(sampleQueueMessage())

        assertTrue(result.isFailure)
        assertEquals("queue unavailable", result.exceptionOrNull()?.message)
    }

    @Test
    fun `save purchase invoice use case should return saved entity`() {
        val invoice = sampleInvoice()
        val gateway = SavePurchaseInvoiceGateway { Result.success(it) }
        val useCase = SavePurchaseInvoiceUseCase(gateway)

        val result = useCase.execute(invoice)

        assertTrue(result.isSuccess)
        assertEquals(invoice.accessKey.value, result.getOrThrow().accessKey.value)
    }

    @Test
    fun `notify purchase invoice queue use case should return success`() {
        val gateway = NotifyPurchaseInvoiceQueueGateway { Result.success(Unit) }
        val useCase = NotifyPurchaseInvoiceQueueUseCase(gateway)

        val result = useCase.execute(sampleInvoiceRequest())

        assertTrue(result.isSuccess)
    }

    private fun sampleNotification() = PaymentNotification(
        cardLastDigits = "1234",
        purchasedAt = LocalDateTime.of(2026, 2, 1, 10, 30),
        amount = BigDecimal("99.90"),
        merchantName = "Loja",
        numberOfInstallments = 1,
        origin = "app",
        originType = "HTTP_REQUEST",
    )

    private fun sampleQueueMessage() = PaymentNotificationQueueMessage(
        text = "Cartao final 1234",
        origin = "app",
        originType = "HTTP_REQUEST",
    )

    private fun sampleInvoice() = PurchaseInvoice(
        date = OffsetDateTime.parse("2026-02-01T10:30:00-03:00"),
        merchantName = "Mercado",
        merchantAddress = "Rua A",
        cnpj = Cnpj.of("12.345.678/0001-90"),
        totalItems = TotalItems.of(1),
        invoiceUrl = InvoiceUrl.of("https://example.com/invoice"),
        accessKey = AccessKey.of("12345678901234567890123456789012345678901234"),
        subtotal = BigDecimal("10.00"),
        total = BigDecimal("10.00"),
        taxes = BigDecimal("0.00"),
        discount = BigDecimal("0.00"),
    )

    private fun sampleInvoiceRequest() = PurchaseInvoiceRequest(
        invoiceUrl = "https://example.com/invoice",
        accessKey = "12345678901234567890123456789012345678901234",
        cnpj = "12.345.678/0001-90",
        merchantName = "Mercado",
        merchantAddress = "Rua A",
        date = "2026-02-01T10:30:00-03:00",
        totalItems = 1,
        items = listOf(
            PurchaseInvoiceItemRequest(
                productName = "Arroz",
                code = "123",
                quantity = 1,
                unitPrice = BigDecimal("10.00"),
                unit = "UN",
                totalPrice = BigDecimal("10.00"),
            )
        ),
        subtotal = BigDecimal("10.00"),
        discount = BigDecimal("0.00"),
        total = BigDecimal("10.00"),
        taxes = BigDecimal("0.00"),
        payments = listOf(
            PurchaseInvoicePaymentRequest(
                type = "CREDIT_CARD",
                value = BigDecimal("10.00"),
            )
        ),
    )
}
