package br.com.nomar.controlai.application.billing

import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.lenient
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// End-to-end tests for POST /webhooks/kiwify against a real database, covering token
// validation, idempotency and the subscription effect of each relevant order_status.
// EmailGateway is replaced with a Mockito mock so no actual emails are sent.
@SpringBootTest
@AutoConfigureMockMvc
class KiwifyWebhookControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean private lateinit var emailGateway: EmailGateway

    private val validToken = "test-kiwify-webhook-token"
    private val annualProductId = "test-annual-product-id"
    private val lifetimeProductId = "test-lifetime-product-id"
    private val testEmail = "kiwify-webhook-test@controlai.test"
    private val newCustomerEmail = "kiwify-webhook-new-customer@controlai.test"
    private var testUserId: Long = 0L
    private var testGroupId: Long = 0L

    @BeforeEach
    fun setupTestUser() {
        cleanUp()

        lenient().`when`(emailGateway.sendWelcomeSetPassword(anyString(), anyString(), anyString())).thenReturn(Result.success(Unit))

        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", "TestGroupKiwify")
        testGroupId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

        jdbcTemplate.update(
            "INSERT INTO users (name, email, password_hash) VALUES (?, ?, ?)",
            "Kiwify Webhook User",
            testEmail,
            "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy",
        )
        testUserId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", testGroupId, testUserId)
    }

    @AfterEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM kiwify_webhook_events WHERE raw_payload LIKE ?", "%$testEmail%")
        if (testGroupId > 0) {
            jdbcTemplate.update("DELETE FROM subscriptions WHERE group_id = ?", testGroupId)
        }
        jdbcTemplate.update("DELETE FROM group_members WHERE user_id IN (SELECT id FROM users WHERE email = ?)", testEmail)
        if (testGroupId > 0) {
            jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", testGroupId)
        }
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", testEmail)
        testUserId = 0L
        testGroupId = 0L

        cleanUpNewCustomer()
    }

    private fun cleanUpNewCustomer() {
        val newCustomerUserId = jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE email = ?",
            Long::class.java,
            newCustomerEmail,
        ).firstOrNull()

        jdbcTemplate.update("DELETE FROM kiwify_webhook_events WHERE raw_payload LIKE ?", "%$newCustomerEmail%")
        jdbcTemplate.update("DELETE FROM password_reset_tokens WHERE user_id = ?", newCustomerUserId ?: -1)
        if (newCustomerUserId != null) {
            val groupId = jdbcTemplate.queryForList(
                "SELECT group_id FROM group_members WHERE user_id = ?",
                Long::class.java,
                newCustomerUserId,
            ).firstOrNull()
            jdbcTemplate.update("DELETE FROM group_members WHERE user_id = ?", newCustomerUserId)
            if (groupId != null) {
                jdbcTemplate.update("DELETE FROM subscriptions WHERE group_id = ?", groupId)
                // SeedDefaultCategoriesGateway seeds categories for every newly created group.
                jdbcTemplate.update("DELETE FROM categories WHERE group_id = ?", groupId)
                jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupId)
            }
        }
        jdbcTemplate.update("DELETE FROM users WHERE email = ?", newCustomerEmail)
    }

    private fun approvedPayload(orderId: String, productId: String) = """
        {"order_id":"$orderId","order_status":"compra_aprovada","customer":{"email":"$testEmail","full_name":"Kiwify Webhook User"},"product":{"product_id":"$productId"}}
    """.trimIndent()

    private fun statusChangePayload(orderId: String, orderStatus: String) = """
        {"order_id":"$orderId","order_status":"$orderStatus","customer":{"email":"$testEmail"}}
    """.trimIndent()

    private fun subscriptionRow(): Map<String, Any?>? =
        jdbcTemplate.queryForList("SELECT plan, status FROM subscriptions WHERE group_id = ?", testGroupId).firstOrNull()

    @Test
    fun `POST without token returns 401`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-no-token", annualProductId)),
        ).andExpect(status().isUnauthorized)
    }

    @Test
    fun `POST with wrong token returns 401`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-wrong-token", annualProductId)),
        ).andExpect(status().isUnauthorized)

        assertNull(subscriptionRow())
    }

    @Test
    fun `POST with malformed JSON body still returns 200 without throwing an unhandled exception`() {
        val malformedBody = "not-a-valid-json-payload{{{"

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(malformedBody),
        ).andExpect(status().isOk)

        // raw_payload is a native MySQL JSON column (Tarefa 2.0), so a syntactically invalid
        // body cannot be persisted for audit either; the use case swallows the failure and the
        // endpoint still returns 200 so Kiwify does not retry aggressively (real Kiwify traffic
        // is always valid JSON, so this only guards against a body that isn't JSON at all).
        val eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kiwify_webhook_events WHERE raw_payload = ?",
            Int::class.java,
            malformedBody,
        )
        assertEquals(0, eventCount)
        assertNull(subscriptionRow())
    }

    @Test
    fun `compra_aprovada with annual product activates ANNUAL subscription`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-annual-1", annualProductId)),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row was not created")
        assertEquals("ANNUAL", row["plan"])
        assertEquals("ACTIVE", row["status"])
    }

    @Test
    fun `compra_aprovada with lifetime product activates LIFETIME subscription`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-lifetime-1", lifetimeProductId)),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row was not created")
        assertEquals("LIFETIME", row["plan"])
        assertEquals("ACTIVE", row["status"])
    }

    @Test
    fun `subscription_canceled cancels an active subscription`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-cancel-1", annualProductId)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusChangePayload("order-cancel-1", "subscription_canceled")),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row should still exist")
        assertEquals("CANCELLED", row["status"])
    }

    @Test
    fun `compra_reembolsada cancels an active subscription`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-refund-1", annualProductId)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusChangePayload("order-refund-1", "compra_reembolsada")),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row should still exist")
        assertEquals("CANCELLED", row["status"])
    }

    @Test
    fun `chargeback cancels an active subscription`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-chargeback-1", annualProductId)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusChangePayload("order-chargeback-1", "chargeback")),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row should still exist")
        assertEquals("CANCELLED", row["status"])
    }

    @Test
    fun `subscription_renewed keeps subscription ACTIVE`() {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(approvedPayload("order-renew-1", annualProductId)),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(statusChangePayload("order-renew-1", "subscription_renewed")),
        ).andExpect(status().isOk)

        val row = subscriptionRow() ?: error("subscription row should still exist")
        assertEquals("ACTIVE", row["status"])
        assertEquals("ANNUAL", row["plan"])
    }

    @Test
    fun `same kiwify event processed twice is idempotent`() {
        val payload = approvedPayload("order-idempotent-1", annualProductId)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isOk)

        val eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kiwify_webhook_events WHERE kiwify_event_id = ?",
            Int::class.java,
            "order-idempotent-1:compra_aprovada",
        )
        assertEquals(1, eventCount)

        val subscriptionCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscriptions WHERE group_id = ?",
            Int::class.java,
            testGroupId,
        )
        assertEquals(1, subscriptionCount)
    }

    @Test
    fun `subscription_canceled for unknown email is recorded but does not create an account`() {
        val unknownEmailPayload = """
            {"order_id":"order-unknown-1","order_status":"subscription_canceled","customer":{"email":"unknown-$testEmail"}}
        """.trimIndent()

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(unknownEmailPayload),
        ).andExpect(status().isOk)

        val eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM kiwify_webhook_events WHERE kiwify_event_id = ?",
            Int::class.java,
            "order-unknown-1:subscription_canceled",
        )
        assertEquals(1, eventCount)

        val userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Int::class.java,
            "unknown-$testEmail",
        )
        assertEquals(0, userCount)
    }

    @Test
    fun `compra_aprovada for a brand-new email creates the account, activates the subscription and sends the set-password email`() {
        val newCustomerPayload = """
            {"order_id":"order-new-customer-1","order_status":"compra_aprovada","customer":{"email":"$newCustomerEmail","full_name":"New Customer"},"product":{"product_id":"$annualProductId"}}
        """.trimIndent()

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(newCustomerPayload),
        ).andExpect(status().isOk)

        val userRow = jdbcTemplate.queryForList(
            "SELECT id, name, password_hash FROM users WHERE email = ?",
            newCustomerEmail,
        ).firstOrNull() ?: error("user was not created for the new customer")
        assertEquals("New Customer", userRow["name"])
        assertNull(userRow["password_hash"])

        val newUserId = userRow["id"] as Long
        val groupId = jdbcTemplate.queryForList(
            "SELECT group_id FROM group_members WHERE user_id = ?",
            Long::class.java,
            newUserId,
        ).firstOrNull() ?: error("personal group was not created for the new customer")

        val subscriptionRow = jdbcTemplate.queryForList(
            "SELECT plan, status FROM subscriptions WHERE group_id = ?",
            groupId,
        ).firstOrNull() ?: error("subscription was not created for the new customer")
        assertEquals("ANNUAL", subscriptionRow["plan"])
        assertEquals("ACTIVE", subscriptionRow["status"])

        val tokenCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?",
            Int::class.java,
            newUserId,
        )
        assertEquals(1, tokenCount)

        verify(emailGateway, times(1)).sendWelcomeSetPassword(anyString(), anyString(), anyString())
    }

    @Test
    fun `sequential repeated compra_aprovada for the same new email never violates uk_users_email and does not duplicate the account`() {
        val newCustomerPayload = """
            {"order_id":"order-repeat-customer-1","order_status":"compra_aprovada","customer":{"email":"$newCustomerEmail","full_name":"New Customer"},"product":{"product_id":"$annualProductId"}}
        """.trimIndent()
        val secondPurchasePayload = """
            {"order_id":"order-repeat-customer-2","order_status":"compra_aprovada","customer":{"email":"$newCustomerEmail","full_name":"New Customer"},"product":{"product_id":"$annualProductId"}}
        """.trimIndent()

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(newCustomerPayload),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPurchasePayload),
        ).andExpect(status().isOk)

        val userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Int::class.java,
            newCustomerEmail,
        )
        assertEquals(1, userCount)

        // The customer never set a password between the two purchases, so the set-password email
        // is resent on the second one too — a deliberate retry path for the case where the first
        // send failed or the account was created by a race-losing request (see the concurrent test
        // below), rather than a bug: it stops once the customer actually sets their password.
        verify(emailGateway, times(2)).sendWelcomeSetPassword(anyString(), anyString(), anyString())
    }

    @Test
    fun `two concurrent compra_aprovada webhooks for the same brand-new email never violate uk_users_email`() {
        // Simulates the annual purchase and the lifetime order bump arriving as two near-
        // simultaneous webhooks for a customer who does not exist yet (Tech Spec: Riscos
        // Conhecidos - "colisao de e-mail... precisa de teste dedicado"). A CyclicBarrier forces
        // both requests to reach the controller at essentially the same instant, so both threads
        // are racing findUserByEmailGateway before either has created the account.
        val barrier = java.util.concurrent.CyclicBarrier(2)
        val executor = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val requests = listOf(
                "order-race-1" to annualProductId,
                "order-race-2" to lifetimeProductId,
            ).map { (orderId, productId) ->
                java.util.concurrent.Callable {
                    barrier.await()
                    mockMvc.perform(
                        post("/webhooks/kiwify")
                            .param("token", validToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(
                                """
                                {"order_id":"$orderId","order_status":"compra_aprovada","customer":{"email":"$newCustomerEmail","full_name":"New Customer"},"product":{"product_id":"$productId"}}
                                """.trimIndent(),
                            ),
                    ).andReturn().response.status
                }
            }

            val statuses = executor.invokeAll(requests).map { it.get() }
            assertTrue(statuses.all { it == 200 }, "both concurrent webhooks must still return 200: $statuses")
        } finally {
            executor.shutdown()
        }

        val userCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM users WHERE email = ?",
            Int::class.java,
            newCustomerEmail,
        )
        assertEquals(1, userCount)

        val newUserId = jdbcTemplate.queryForObject(
            "SELECT id FROM users WHERE email = ?",
            Long::class.java,
            newCustomerEmail,
        )
        val groupCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM group_members WHERE user_id = ?",
            Int::class.java,
            newUserId,
        )
        assertEquals(1, groupCount)

        val subscriptionCount = jdbcTemplate.queryForList(
            "SELECT s.status FROM subscriptions s JOIN group_members gm ON gm.group_id = s.group_id WHERE gm.user_id = ?",
            newUserId,
        ).size
        assertEquals(1, subscriptionCount)
    }
}
