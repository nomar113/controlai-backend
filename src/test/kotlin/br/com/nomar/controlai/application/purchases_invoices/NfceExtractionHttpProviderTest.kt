package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.purchases_invoices.application.NfceExtractionHttpProvider
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionBlockedException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionNavigationException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionTimeoutException
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withServerError
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.web.client.RestClient
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NfceExtractionHttpProviderTest {

    private val baseUrl = "http://localhost:3000"
    private val internalKey = "test-internal-key"
    private val invoiceUrl = InvoiceUrl.of("https://www.example.com/nfce?p=12345678901234567890123456789012345678901234")

    private lateinit var mockServer: MockRestServiceServer
    private lateinit var provider: NfceExtractionHttpProvider

    private fun setUp() {
        val builder = RestClient.builder()
        mockServer = MockRestServiceServer.bindTo(builder).build()
        provider = NfceExtractionHttpProvider(builder, baseUrl, internalKey)
    }

    @Test
    fun `should return extracted invoice when service responds READY`() {
        setUp()
        mockServer.expect(ExpectedCount.once(), requestTo("$baseUrl/extract"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("X-Internal-Key", internalKey))
            .andExpect(jsonPath("$.invoiceUrl").value(invoiceUrl.asString()))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(
                        """
                        {
                          "status": "READY",
                          "data": {
                            "merchantName": "Mercado Teste",
                            "cnpj": "12345678000199",
                            "merchantAddress": "Rua A, 123",
                            "totalItems": 1,
                            "subtotal": 10.00,
                            "discount": 0.00,
                            "total": 10.00,
                            "taxes": 1.50,
                            "date": "28/08/2026 10:00:00-03:00",
                            "items": [
                              {"productName": "Produto A", "code": "001", "quantity": 1, "unit": "UN", "unitPrice": 10.00, "totalPrice": 10.00}
                            ],
                            "payments": [
                              {"type": "DINHEIRO", "value": 10.00}
                            ]
                          }
                        }
                        """.trimIndent()
                    )
            )

        val result = provider.extract(invoiceUrl)

        assertTrue(result.isSuccess)
        val extracted = result.getOrThrow()
        assertEquals("Mercado Teste", extracted.merchantName)
        assertEquals(1, extracted.items.size)
        assertEquals("Produto A", extracted.items.first().productName)
        assertEquals(1, extracted.payments.size)
        mockServer.verify()
    }

    @Test
    fun `should fail with blocked exception when service responds BLOCKED`() {
        setUp()
        mockServer.expect(requestTo("$baseUrl/extract"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status": "BLOCKED", "message": "Nota bloqueada pela SEFAZ"}""")
            )

        val result = provider.extract(invoiceUrl)

        assertTrue(result.isFailure)
        val exception = assertIs<NfceExtractionBlockedException>(result.exceptionOrNull())
        assertEquals("Nota bloqueada pela SEFAZ", exception.message)
        mockServer.verify()
    }

    @Test
    fun `should fail with timeout exception when service responds TIMEOUT`() {
        setUp()
        mockServer.expect(requestTo("$baseUrl/extract"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status": "TIMEOUT"}""")
            )

        val result = provider.extract(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionTimeoutException>(result.exceptionOrNull())
        mockServer.verify()
    }

    @Test
    fun `should fail with navigation exception when service responds NAVIGATION_ERROR`() {
        setUp()
        mockServer.expect(requestTo("$baseUrl/extract"))
            .andRespond(
                withStatus(HttpStatus.OK)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body("""{"status": "NAVIGATION_ERROR"}""")
            )

        val result = provider.extract(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionNavigationException>(result.exceptionOrNull())
        mockServer.verify()
    }

    @Test
    fun `should fail with navigation exception when service is unavailable`() {
        setUp()
        mockServer.expect(requestTo("$baseUrl/extract"))
            .andRespond(withServerError())

        val result = provider.extract(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionNavigationException>(result.exceptionOrNull())
        mockServer.verify()
    }
}
