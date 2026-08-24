package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.auth.entrypoint.rest.response.ApiKeyResponse
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.AssociateInvoiceResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseInvoiceDetailResponse
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.response.PurchaseResponse
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant

/**
 * Tarefa 4.0/4.1: valida que os DTOs de response afetados expoem `Instant` nativo (serializado
 * como ISO-8601 UTC com sufixo `Z` pelo ObjectMapper real da aplicacao), sem `.toString()`
 * manual nem `@JsonFormat` customizado.
 */
@SpringBootTest
class ResponseDtoDateSerializationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val instant: Instant = Instant.parse("2026-01-15T13:00:00Z")

    @Test
    fun `PaymentNotificationResponse purchasedAt and cancelledAt serialize as ISO-8601 UTC instant`() {
        val response = PaymentNotificationResponse(
            id = 1L,
            purchasedAt = instant,
            amount = BigDecimal("10.00"),
            merchantName = "Loja",
            numberOfInstallments = 1,
            cancelledAt = instant,
        )

        val json = objectMapper.writeValueAsString(response)

        assertTrue(json.contains("\"purchasedAt\":\"2026-01-15T13:00:00Z\""))
        assertTrue(json.contains("\"cancelledAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `PurchaseResponse date and cancelledAt serialize as ISO-8601 UTC instant`() {
        val response = PurchaseResponse(
            id = 1L,
            date = instant,
            merchantName = "Mercado",
            totalItems = 3,
            total = BigDecimal("50.00"),
            cancelledAt = instant,
        )

        val json = objectMapper.writeValueAsString(response)

        assertTrue(json.contains("\"date\":\"2026-01-15T13:00:00Z\""))
        assertTrue(json.contains("\"cancelledAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `PurchaseInvoiceDetailResponse date and cancelledAt serialize as ISO-8601 UTC instant`() {
        val response = PurchaseInvoiceDetailResponse(
            id = 1L,
            date = instant,
            merchantName = "Mercado",
            merchantAddress = "Rua A",
            cnpj = "12345678000199",
            totalItems = 1,
            invoiceUrl = null,
            accessKey = null,
            subtotal = BigDecimal("10.00"),
            total = BigDecimal("10.00"),
            taxes = BigDecimal.ZERO,
            discount = BigDecimal.ZERO,
            description = null,
            cancelledAt = instant,
            items = emptyList(),
            payments = emptyList(),
            associatedPayment = null,
        )

        val json = objectMapper.writeValueAsString(response)

        assertTrue(json.contains("\"date\":\"2026-01-15T13:00:00Z\""))
        assertTrue(json.contains("\"cancelledAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `AssociateInvoiceResponse associatedAt serializes as ISO-8601 UTC instant`() {
        val response = AssociateInvoiceResponse(
            invoiceId = 1L,
            paymentNotificationId = 2L,
            associatedAt = instant,
        )

        val json = objectMapper.writeValueAsString(response)

        assertTrue(json.contains("\"associatedAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `ApiKeyResponse createdAt serializes as ISO-8601 UTC instant`() {
        val response = ApiKeyResponse(
            id = 1L,
            label = "Meu app",
            createdAt = instant,
            revoked = false,
        )

        val json = objectMapper.writeValueAsString(response)

        assertTrue(json.contains("\"createdAt\":\"2026-01-15T13:00:00Z\""))
    }
}
