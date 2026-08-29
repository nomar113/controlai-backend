package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionBlockedException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionNavigationException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionTimeoutException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NfceExtractionGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

// POST /purchases/invoice/extraction — NfceExtractionGateway is replaced with a fake bean (a
// lambda fun interface, per the project's gateway-testing convention) so tests never call the
// real nfce-extraction-service. The rest of the stack (DB, Spring Security, JWT) runs for real,
// mirroring AssociateInvoiceControllerIntegrationTest.
@TestConfiguration
class FakeNfceExtractionGatewayConfig {

    companion object {
        val result = AtomicReference<Result<ExtractedPurchaseInvoice>>(
            Result.failure(UnsupportedOperationException("gateway result not stubbed for this test"))
        )
    }

    @Bean
    @Primary
    fun nfceExtractionGateway(): NfceExtractionGateway = NfceExtractionGateway { result.get() }
}

@SpringBootTest
@AutoConfigureMockMvc
@Import(FakeNfceExtractionGatewayConfig::class)
class ExtractPurchaseInvoiceControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var webApplicationContext: WebApplicationContext

    private val newAccessKey = "1".repeat(44)
    private val registeredAccessKey = "2".repeat(44)

    private fun invoiceUrl(accessKey: String) =
        "https://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$accessKey|2|1|1|abc123"

    private fun stubGateway(result: Result<ExtractedPurchaseInvoice>) {
        FakeNfceExtractionGatewayConfig.result.set(result)
    }

    private val extractedInvoice = ExtractedPurchaseInvoice(
        merchantName = "Mercado Teste",
        cnpj = "12345678000199",
        merchantAddress = "Rua A, 123",
        totalItems = 1,
        subtotal = BigDecimal("10.00"),
        discount = BigDecimal.ZERO,
        total = BigDecimal("10.00"),
        taxes = BigDecimal("1.50"),
        date = "28/08/2026 10:00:00-03:00",
        items = emptyList(),
        payments = emptyList(),
    )

    @BeforeEach
    fun cleanUp() = clearTables()

    @AfterEach
    fun cleanUpAfter() = clearTables()

    private fun clearTables() {
        jdbcTemplate.update("DELETE FROM purchase_payments")
        jdbcTemplate.update("DELETE FROM purchase_items")
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    private fun insertInvoiceWithAccessKey(accessKey: String) {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices
               (date, merchant_name, merchant_address, cnpj, invoice_url, access_key, subtotal, total, taxes, discount, group_id)
               VALUES (NOW(), ?, '', '', '', ?, 0, 0, 0, 0, 1)""",
            "Mercado Teste ${UUID.randomUUID()}",
            accessKey,
        )
    }

    @Test
    fun `POST invoice extraction should return 200 with extracted data on success`() {
        stubGateway(Result.success(extractedInvoice))

        mockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(newAccessKey)}"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.merchantName").value("Mercado Teste"))
            .andExpect(jsonPath("$.total").value(10.00))
    }

    @Test
    fun `POST invoice extraction should return 409 when accessKey already registered`() {
        insertInvoiceWithAccessKey(registeredAccessKey)

        mockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(registeredAccessKey)}"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST invoice extraction should return 422 when SEFAZ blocks the query`() {
        stubGateway(Result.failure(NfceExtractionBlockedException("Nota bloqueada pela SEFAZ")))

        mockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(newAccessKey)}"}""")
        )
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `POST invoice extraction should return 504 on timeout`() {
        stubGateway(Result.failure(NfceExtractionTimeoutException()))

        mockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(newAccessKey)}"}""")
        )
            .andExpect(status().isGatewayTimeout)
    }

    @Test
    fun `POST invoice extraction should return 502 on navigation or service error`() {
        stubGateway(Result.failure(NfceExtractionNavigationException()))

        mockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(newAccessKey)}"}""")
        )
            .andExpect(status().isBadGateway)
    }

    @Test
    fun `POST invoice extraction should return 401 without auth token`() {
        // TestAuthMockMvcConfig injects a default Bearer token into every request on the
        // autowired mockMvc, so anonymous-access tests need their own raw MockMvc instance
        // (same pattern as GroupInviteIntegrationTest).
        val rawMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()

        rawMockMvc.perform(
            post("/purchases/invoice/extraction")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"invoiceUrl": "${invoiceUrl(newAccessKey)}"}""")
        )
            .andExpect(status().isUnauthorized)
    }
}
