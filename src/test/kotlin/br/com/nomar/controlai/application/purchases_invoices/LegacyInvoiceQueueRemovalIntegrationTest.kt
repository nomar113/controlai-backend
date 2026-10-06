package br.com.nomar.controlai.application.purchases_invoices

import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.PaymentNotificationQueueListener
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.core.env.Environment
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post

// The old invoice flow (POST /purchases/invoice + purchases-invoices SQS queue) was replaced by
// POST /purchases/invoices/pending; payment notifications must keep using SQS.
@SpringBootTest
@AutoConfigureMockMvc
class LegacyInvoiceQueueRemovalIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var applicationContext: ApplicationContext
    @Autowired private lateinit var environment: Environment

    @Test
    fun `POST purchases invoice should no longer be routed`() {
        val status = mockMvc.perform(
            post("/purchases/invoice").contentType(MediaType.APPLICATION_JSON).content("{}")
        ).andReturn().response.status

        assertTrue(status == 404 || status == 405, "expected 404 or 405 but was $status")
    }

    @Test
    fun `context should load the payment notifications listener and not the invoices queue`() {
        assertEquals(1, applicationContext.getBeanNamesForType(PaymentNotificationQueueListener::class.java).size)
        assertTrue(environment.containsProperty("aws.sqs.payments-notifications-queue-url"))
        assertFalse(environment.containsProperty("aws.sqs.purchases-invoices-queue-url"))
    }
}
