package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.auth.entrypoint.database.model.ApiKeyModel
import br.com.nomar.controlai.application.auth.entrypoint.database.model.PasswordResetTokenModel
import br.com.nomar.controlai.application.auth.entrypoint.database.model.RefreshTokenModel
import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupInviteModel
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

/**
 * Valida que todo timestamp de evento (Instant) serializa como ISO-8601 UTC com sufixo `Z`
 * e toda data de calendario (LocalDate) serializa como `YYYY-MM-DD`, usando o ObjectMapper
 * real da aplicacao (configurado com spring.jackson.time-zone=UTC na Tarefa 1.0) — sem
 * @JsonFormat customizado nem .toString() manual nas entidades padronizadas na Tarefa 2.0.
 */
@SpringBootTest
class EntityDateSerializationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val instant: Instant = Instant.parse("2026-01-15T13:00:00Z")
    private val date: LocalDate = LocalDate.of(2026, 1, 15)

    @Test
    fun `PaymentNotification purchasedAt serializes as ISO-8601 UTC instant`() {
        val notification = PaymentNotification(
            purchasedAt = instant,
            amount = BigDecimal("10.00"),
            merchantName = "Loja",
        )

        val json = objectMapper.writeValueAsString(notification)

        assertTrue(json.contains("\"purchasedAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `Installment dueDate serializes as plain calendar date and cancelledAt as ISO-8601 UTC instant`() {
        val installment = Installment(
            parentId = 1,
            installmentNumber = 1,
            totalInstallments = 1,
            amount = BigDecimal("10.00"),
            dueDate = date,
            cancelledAt = instant,
        )

        val json = objectMapper.writeValueAsString(installment)

        assertTrue(json.contains("\"dueDate\":\"2026-01-15\""))
        assertTrue(json.contains("\"cancelledAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `PurchaseInvoice date deserializes the NFC-e producer offset format into the correct UTC instant`() {
        // Formato real enviado pelo producer externo de NFC-e via fila SQS
        // (@JsonFormat(pattern = "dd/MM/yyyy HH:mm:ssXXX") em PurchaseInvoice.date), sempre com
        // o offset correto de America/Sao_Paulo. Ver PurchaseInvoiceQueueListener.
        val json = """
            {
              "date": "15/01/2026 10:00:00-03:00",
              "merchantName": "Mercado",
              "merchantAddress": "Rua A",
              "cnpj": "12.345.678/0001-90",
              "totalItems": 1,
              "invoiceUrl": "https://example.com/invoice",
              "accessKey": "12345678901234567890123456789012345678901234",
              "subtotal": 10.00,
              "total": 10.00,
              "taxes": 0.00,
              "discount": 0.00
            }
        """.trimIndent()

        val invoice = objectMapper.readValue(json, PurchaseInvoice::class.java)

        assertEquals(Instant.parse("2026-01-15T13:00:00Z"), invoice.date)
    }

    @Test
    fun `PurchaseInvoiceModel date serializes as ISO-8601 UTC instant`() {
        val invoice = PurchaseInvoiceModel(
            date = instant,
            merchantName = "Mercado",
            merchantAddress = null,
            cnpj = null,
            totalItems = null,
            invoiceUrl = null,
            accessKey = null,
            subtotal = null,
            total = null,
            taxes = null,
            discount = null,
        )

        val json = objectMapper.writeValueAsString(invoice)

        assertTrue(json.contains("\"date\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `GroupInviteModel expiresAt serializes as ISO-8601 UTC instant`() {
        val invite = GroupInviteModel(expiresAt = instant)

        val json = objectMapper.writeValueAsString(invite)

        assertTrue(json.contains("\"expiresAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `ApiKeyModel revokedAt serializes as ISO-8601 UTC instant`() {
        val apiKey = ApiKeyModel(keyHash = "hash", revokedAt = instant)

        val json = objectMapper.writeValueAsString(apiKey)

        assertTrue(json.contains("\"revokedAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `RefreshTokenModel expiresAt serializes as ISO-8601 UTC instant`() {
        val token = RefreshTokenModel(tokenHash = "hash", expiresAt = instant, absoluteExpiresAt = instant)

        val json = objectMapper.writeValueAsString(token)

        assertTrue(json.contains("\"expiresAt\":\"2026-01-15T13:00:00Z\""))
    }

    @Test
    fun `PasswordResetTokenModel expiresAt serializes as ISO-8601 UTC instant`() {
        val token = PasswordResetTokenModel(tokenHash = "hash", expiresAt = instant)

        val json = objectMapper.writeValueAsString(token)

        assertTrue(json.contains("\"expiresAt\":\"2026-01-15T13:00:00Z\""))
    }
}
