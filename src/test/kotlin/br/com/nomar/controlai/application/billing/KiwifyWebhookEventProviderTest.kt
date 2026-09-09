package br.com.nomar.controlai.application.billing

import br.com.nomar.controlai.application.billing.application.FindKiwifyWebhookEventByIdProvider
import br.com.nomar.controlai.application.billing.application.SaveKiwifyWebhookEventProvider
import br.com.nomar.controlai.domain.billing.entity.KiwifyWebhookEvent
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class KiwifyWebhookEventProviderTest {

    @Autowired
    private lateinit var saveProvider: SaveKiwifyWebhookEventProvider

    @Autowired
    private lateinit var findProvider: FindKiwifyWebhookEventByIdProvider

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val createdEventIds = mutableListOf<String>()

    @AfterEach
    fun cleanup() {
        createdEventIds.forEach { eventId ->
            jdbcTemplate.update("DELETE FROM kiwify_webhook_events WHERE kiwify_event_id = ?", eventId)
        }
        createdEventIds.clear()
    }

    @Test
    fun `should return null when the event was not processed yet`() {
        val result = findProvider.execute("evt-not-found-${System.nanoTime()}")

        assertTrue(result.isSuccess)
        assertNull(result.getOrThrow())
    }

    @Test
    fun `should save and find a webhook event by its kiwify event id`() {
        val eventId = "evt-save-${System.nanoTime()}"
        createdEventIds.add(eventId)

        val saveResult = saveProvider.execute(
            KiwifyWebhookEvent(
                kiwifyEventId = eventId,
                orderStatus = "compra_aprovada",
                rawPayload = "{\"order_status\":\"compra_aprovada\"}",
            )
        )

        assertTrue(saveResult.isSuccess)
        assertNotNull(saveResult.getOrThrow().id)

        val findResult = findProvider.execute(eventId)

        assertTrue(findResult.isSuccess)
        val found = findResult.getOrThrow()
        assertEquals(eventId, found?.kiwifyEventId)
        assertEquals("compra_aprovada", found?.orderStatus)
    }

    @Test
    fun `should reject saving a duplicate kiwify event id`() {
        val eventId = "evt-duplicate-${System.nanoTime()}"
        createdEventIds.add(eventId)

        saveProvider.execute(
            KiwifyWebhookEvent(kiwifyEventId = eventId, orderStatus = "compra_aprovada", rawPayload = "{}")
        )

        val duplicate = saveProvider.execute(
            KiwifyWebhookEvent(kiwifyEventId = eventId, orderStatus = "compra_aprovada", rawPayload = "{}")
        )

        assertTrue(duplicate.isFailure)
    }
}
