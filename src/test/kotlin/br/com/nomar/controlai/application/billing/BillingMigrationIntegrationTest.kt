package br.com.nomar.controlai.application.billing

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Legacy group seeded by V29 (before the commercial launch existed), used as a stable fixture
// to assert grandfathering — the total row counts of `groups`/`subscriptions` are not reliable
// assertions here since other integration tests keep creating new groups in this shared database.
@SpringBootTest
class BillingMigrationIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    private val legacyGroupId = 1L

    @Test
    fun `should create subscriptions table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'SUBSCRIPTIONS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should create kiwify_webhook_events table`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE UPPER(table_name) = 'KIWIFY_WEBHOOK_EVENTS'",
            Int::class.java
        )
        assertEquals(1, count)
    }

    @Test
    fun `should have correct columns in subscriptions table`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'SUBSCRIPTIONS'",
        ).map { it["COL"] as String }

        listOf(
            "ID", "GROUP_ID", "PLAN", "STATUS", "KIWIFY_ORDER_ID",
            "CURRENT_PERIOD_END", "CREATED_AT", "UPDATED_AT"
        ).forEach { assertTrue(columns.contains(it), "subscriptions should have column $it") }
    }

    @Test
    fun `should have correct columns in kiwify_webhook_events table`() {
        val columns = jdbcTemplate.queryForList(
            "SELECT UPPER(column_name) as col FROM information_schema.columns WHERE UPPER(table_name) = 'KIWIFY_WEBHOOK_EVENTS'",
        ).map { it["COL"] as String }

        listOf("ID", "KIWIFY_EVENT_ID", "ORDER_STATUS", "RAW_PAYLOAD", "PROCESSED_AT").forEach {
            assertTrue(columns.contains(it), "kiwify_webhook_events should have column $it")
        }
    }

    @Test
    fun `should enforce unique constraint on subscriptions group_id`() {
        // legacyGroupId already has a subscription row inserted by V39's own backfill
        // (independent of the execution order of the tests in this class).
        val exception = runCatching {
            jdbcTemplate.update(
                "INSERT INTO subscriptions (group_id, plan, status) VALUES (?, 'ANNUAL', 'ACTIVE')",
                legacyGroupId
            )
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should enforce foreign key constraint from subscriptions to groups`() {
        val nonExistentGroupId = -1L
        val exception = runCatching {
            jdbcTemplate.update(
                "INSERT INTO subscriptions (group_id, plan, status) VALUES (?, 'ANNUAL', 'ACTIVE')",
                nonExistentGroupId
            )
        }
        assertTrue(exception.isFailure)
    }

    @Test
    fun `should enforce unique constraint on kiwify_webhook_events kiwify_event_id`() {
        val eventId = "evt-unique-test-${System.nanoTime()}"
        jdbcTemplate.update(
            "INSERT INTO kiwify_webhook_events (kiwify_event_id, order_status, raw_payload) VALUES (?, ?, ?)",
            eventId, "compra_aprovada", "{}"
        )

        val exception = runCatching {
            jdbcTemplate.update(
                "INSERT INTO kiwify_webhook_events (kiwify_event_id, order_status, raw_payload) VALUES (?, ?, ?)",
                eventId, "compra_aprovada", "{}"
            )
        }
        assertTrue(exception.isFailure)

        jdbcTemplate.update("DELETE FROM kiwify_webhook_events WHERE kiwify_event_id = ?", eventId)
    }

    @Test
    fun `should backfill GRANDFATHERED ACTIVE subscription for the legacy pre-existing group`() {
        val rows = jdbcTemplate.queryForList(
            "SELECT plan, status FROM subscriptions WHERE group_id = ?", legacyGroupId
        )

        assertEquals(1, rows.size, "legacy group should have exactly one subscription row")
        assertEquals("GRANDFATHERED", rows[0]["plan"])
        assertEquals("ACTIVE", rows[0]["status"])
    }
}
