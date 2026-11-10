package br.com.nomar.controlai.application.nfce_processor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType

// Over a real servlet container: an error answered by the open endpoints is forwarded to /error,
// which MockMvc never does. Without /error open, the processor got 401 instead of the real status.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class NfceProcessorErrorDispatchIntegrationTest {

    @Autowired private lateinit var restTemplate: TestRestTemplate

    private fun post(path: String, json: String) = restTemplate.postForEntity(
        "/internal/nfce-processor$path",
        HttpEntity(json, HttpHeaders().apply { contentType = MediaType.APPLICATION_JSON }),
        String::class.java,
    )

    @Test
    fun `a malformed result should answer 400, not 401`() {
        val response = post("/invoices/1/result", """{"accessKey": "123"}""")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `an invalid failure should answer 400, not 401`() {
        val response = post("/invoices/1/failure", """{"accessKey": "123", "reason": "TIMEOUT"}""")

        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
    }

    @Test
    fun `an error on an authenticated route should still answer 401 without a token`() {
        val response = restTemplate.getForEntity("/purchases", String::class.java)

        assertEquals(HttpStatus.UNAUTHORIZED, response.statusCode)
    }
}
