package br.com.nomar.controlai.application.purchases_invoices

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
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class RegisterPendingInvoiceControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var databaseCleaner: TestDatabaseCleaner

    @BeforeEach
    fun cleanUp() = databaseCleaner.deleteFinancialData()

    @AfterEach
    fun cleanUpAfter() = databaseCleaner.deleteFinancialData()

    private fun register(body: String): ResultActions = mockMvc.perform(
        post("/purchases/invoices/pending").contentType(MediaType.APPLICATION_JSON).content(body)
    )

    private fun qrBody(content: String) = """{"qrCodeContent": "$content"}"""

    @Test
    fun `POST pending should return 201 and store a PENDING invoice in the request group`() {
        register(qrBody(QR_CONTENT))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNumber)
            .andExpect(jsonPath("$.accessKey").value(ACCESS_KEY))
            .andExpect(jsonPath("$.invoiceUrl").value(QR_CONTENT))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.date").exists())

        val row = jdbcTemplate.queryForMap(
            "SELECT group_id, status, invoice_url FROM purchase_invoices WHERE access_key = ?", ACCESS_KEY,
        )
        assertEquals(1L, (row["group_id"] as Number).toLong())
        assertEquals("PENDING", row["status"])
        assertEquals(QR_CONTENT, row["invoice_url"])
    }

    @Test
    fun `POST pending should return 400 with the user-facing message when the QR Code is not an NFC-e`() {
        register(qrBody("https://example.com/promo?p=123"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.message").value("Este QR Code não é de uma nota fiscal de consumidor (NFC-e)"))

        assertEquals(0, countInvoices())
    }

    @Test
    fun `POST pending should return 409 when the invoice was already registered in the group`() {
        register(qrBody(QR_CONTENT)).andExpect(status().isCreated)

        register(qrBody(QR_CONTENT))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.message").value("Esta nota já foi registrada"))

        assertEquals(1, countInvoices())
    }

    @Test
    fun `POST pending should accept a 1024 character QR Code`() {
        register(qrBody(QR_CONTENT.padEnd(1024, 'A'))).andExpect(status().isCreated)

        assertEquals(1, countInvoices())
    }

    @Test
    fun `POST pending should return 400 for a blank, missing or too long qrCodeContent`() {
        register(qrBody("   ")).andExpect(status().isBadRequest)
        register("{}").andExpect(status().isBadRequest)
        // invoice_url holds 1024 characters: one more must be refused before reaching the database
        register(qrBody(QR_CONTENT.padEnd(1025, 'A'))).andExpect(status().isBadRequest)

        assertEquals(0, countInvoices())
    }

    private fun countInvoices(): Int =
        jdbcTemplate.queryForObject("SELECT COUNT(*) FROM purchase_invoices", Int::class.java)!!

    private companion object {
        const val ACCESS_KEY = "33260253358724000682650010000901721678115882"
        const val QR_CONTENT = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$ACCESS_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
    }
}
