package br.com.nomar.controlai.application.nfce_processor

import br.com.nomar.controlai.config.TestDatabaseCleaner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDateTime

// The result endpoint is open, so the processor calls go through a raw MockMvc without the default
// Bearer token of TestAuthMockMvcConfig; the app calls (group 1) use the authenticated mockMvc.
@SpringBootTest
@AutoConfigureMockMvc
class ProcessInvoiceReadResultEndpointIntegrationTest {

    @Autowired private lateinit var webApplicationContext: WebApplicationContext
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    private lateinit var anonymousMockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        anonymousMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
            .apply<DefaultMockMvcBuilder>(springSecurity())
            .build()
        databaseCleaner.deleteFinancialData()
    }

    @AfterEach
    fun tearDown() = databaseCleaner.deleteFinancialData()

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    // Scanned in November, issued in October: the processed invoice must move to the issue month
    private fun insertPendingInvoice(status: String = "PENDING"): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices (date, invoice_url, access_key, status, group_id, created_at)
               VALUES (?, ?, ?, ?, 1, ?)""",
            SCANNED_AT, "$SEFAZ_URL$ACCESS_KEY|2|1|1|hash", ACCESS_KEY, status, SCANNED_AT,
        )
        return lastInsertId()
    }

    private fun postResult(id: Long, body: String): ResultActions = anonymousMockMvc.perform(
        post("/internal/nfce-processor/invoices/$id/result")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
    )

    // Items 27.90 + 16.98 = 44.88, minus 1.00 discount = 43.88, paid 50.00 with 6.12 change
    private fun resultBody(
        accessKey: String = ACCESS_KEY,
        total: String = "43.88",
        issuedAt: String? = "\"2026-10-20T14:32:10-03:00\"",
    ) = """
        {
          "accessKey": "$accessKey",
          "merchantName": "Supermercado Exemplo",
          "cnpj": "12.345.678/0001-90",
          "merchantAddress": "Rua Exemplo, 100, Rio de Janeiro",
          "totalItems": 2,
          "subtotal": 44.88,
          "discount": 1.00,
          "taxes": 5.10,
          "total": $total,
          "change": 6.12,
          ${issuedAt?.let { "\"issuedAt\": $it," } ?: ""}
          "items": [
            {"productName": "ARROZ TIPO 1 5KG", "code": "7890000000011", "quantity": 1.000, "unit": "UN", "unitPrice": 27.90, "totalPrice": 27.90},
            {"productName": "FEIJAO CARIOCA 1KG", "code": "7890000000028", "quantity": 2.000, "unit": "UN", "unitPrice": 8.49, "totalPrice": 16.98}
          ],
          "payments": [
            {"type": "Dinheiro", "value": 30.00},
            {"type": "Cartão de Débito", "value": 20.00}
          ]
        }
    """.trimIndent()

    private fun status(id: Long): String =
        jdbcTemplate.queryForObject("SELECT status FROM purchase_invoices WHERE id = ?", String::class.java, id)!!

    private fun itemCount(id: Long): Int = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM purchase_items WHERE purchase_invoice_id = ?", Int::class.java, id,
    )!!

    @Test
    fun `a valid result without Authorization should answer 200 PROCESSED`() {
        val id = insertPendingInvoice()

        postResult(id, resultBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("PROCESSED"))

        assertEquals("PROCESSED", status(id))
        assertEquals(2, itemCount(id))
    }

    @Test
    fun `a result for an invoice already processed or with another access key should answer 200 DISCARDED`() {
        val id = insertPendingInvoice()
        postResult(id, resultBody()).andExpect(jsonPath("$.outcome").value("PROCESSED"))

        postResult(id, resultBody())
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("DISCARDED"))
        postResult(id, resultBody(accessKey = OTHER_ACCESS_KEY))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.outcome").value("DISCARDED"))

        assertEquals(2, itemCount(id))
    }

    @Test
    fun `an inconsistent result should answer 422 with the reason and change nothing`() {
        val id = insertPendingInvoice()

        postResult(id, resultBody(total = "40.00"))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.reason").value("INCONSISTENT_DATA"))
            .andExpect(jsonPath("$.message").isNotEmpty)

        assertEquals("PENDING", status(id))
        assertEquals(0, itemCount(id))
    }

    @Test
    fun `a result without issuedAt should answer 422 INCOMPLETE_DATA`() {
        val id = insertPendingInvoice()

        postResult(id, resultBody(issuedAt = null))
            .andExpect(status().isUnprocessableEntity)
            .andExpect(jsonPath("$.reason").value("INCOMPLETE_DATA"))

        assertEquals("PENDING", status(id))
    }

    @Test
    fun `a malformed payload should answer 400 and change nothing`() {
        val id = insertPendingInvoice()

        listOf(
            "{not json",
            """{"accessKey": "$ACCESS_KEY"}""",
            resultBody(accessKey = "123"),
            resultBody(total = "\"abc\""),
            resultBody(total = "-1.00"),
            resultBody(issuedAt = "\"20/10/2026 14:32:10\""),
            resultBody().replace("\"Dinheiro\"", "\"\""),
            resultBody().replace("ARROZ TIPO 1 5KG", "X".repeat(300)),
            resultBody().replace("\"change\": 6.12", "\"change\": 123456789.00"),
        ).forEach { body ->
            postResult(id, body).andExpect(status().isBadRequest)
        }

        assertEquals("PENDING", status(id))
        assertEquals(0, itemCount(id))
    }

    @Test
    fun `the processed invoice should count in the issue month and accept categorize, associate and cancel`() {
        val id = insertPendingInvoice()
        postResult(id, resultBody()).andExpect(jsonPath("$.outcome").value("PROCESSED"))
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES ('2026-10-20 17:40:00', 43.88, 'Notif Mercado', 1, 'MANUAL', 'MANUAL', 1)""",
        )
        val notificationId = lastInsertId()
        jdbcTemplate.update("INSERT INTO categories (name, group_id) VALUES ('Mercado NFC-e', 1)")
        val categoryId = lastInsertId()

        mockMvc.perform(get("/purchases/invoices").param("month", "2026-10"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value(id))
            .andExpect(jsonPath("$.content[0].status").value("PROCESSED"))
            .andExpect(jsonPath("$.content[0].total").value(43.88))
            .andExpect(jsonPath("$.content[0].merchantName").value("Supermercado Exemplo"))
        mockMvc.perform(get("/purchases/invoices").param("month", "2026-11"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.totalElements").value(0))
        mockMvc.perform(get("/purchases/invoices/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.payments.length()").value(2))

        mockMvc.perform(get("/purchases/invoices/$id/suggestions/search"))
            .andExpect(status().isOk)
        mockMvc.perform(
            patch("/purchases/invoices/$id/associate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"paymentNotificationId": $notificationId}""")
        ).andExpect(status().isOk)
        // The app categorizes a purchase through its payment notification
        mockMvc.perform(
            patch("/payments/notifications/$notificationId/category")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"categoryId": $categoryId}""")
        ).andExpect(status().isOk)
        mockMvc.perform(patch("/purchases/invoices/$id/cancel"))
            .andExpect(status().isOk)

        assertEquals(id, jdbcTemplate.queryForObject(
            "SELECT purchase_invoice_id FROM payment_notifications WHERE id = ?", Long::class.java, notificationId,
        ))
        assertEquals(categoryId, jdbcTemplate.queryForObject(
            "SELECT category_id FROM payment_notifications WHERE id = ?", Long::class.java, notificationId,
        ))
    }

    private companion object {
        const val SEFAZ_URL = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p="
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val OTHER_ACCESS_KEY = "33260253358724000682650010000901721678115899"
        val SCANNED_AT: LocalDateTime = LocalDateTime.of(2026, 11, 5, 12, 0)
    }
}
