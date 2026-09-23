package br.com.nomar.controlai.application.account_deletion

import br.com.nomar.controlai.application.account_deletion.application.AccountPurgeProvider
import br.com.nomar.controlai.application.account_deletion.entrypoint.scheduler.AccountPurgeScheduler
import br.com.nomar.controlai.domain.account_deletion.entity.PurgeOutcome
import br.com.nomar.controlai.domain.account_deletion.usecase.PurgeDueAccountsUseCase
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.lenient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

// The purge against the real schema: every row of a sole member's group must be gone, while a
// shared group keeps everything but the member who left.
@SpringBootTest
@AutoConfigureMockMvc
class AccountPurgeIntegrationTest {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var purgeDueAccountsUseCase: PurgeDueAccountsUseCase
    @Autowired private lateinit var accountPurgeProvider: AccountPurgeProvider
    @Autowired private lateinit var applicationContext: ApplicationContext

    @MockitoBean private lateinit var emailGateway: EmailGateway

    private val soleEmail = "purge-sole@controlai.test"
    private val leavingEmail = "purge-leaving@controlai.test"
    private val stayingEmail = "purge-staying@controlai.test"
    private val notDueEmail = "purge-not-due@controlai.test"
    private val allEmails = listOf(soleEmail, leavingEmail, stayingEmail, notDueEmail)

    private val due = Timestamp.from(Instant.now().minus(Duration.ofDays(1)))
    private val notDue = Timestamp.from(Instant.now().plus(Duration.ofDays(1)))

    @BeforeEach
    fun setUp() {
        cleanUp()
        lenient().`when`(emailGateway.sendWelcomeSetPassword(anyString(), anyString(), anyString()))
            .thenReturn(Result.success(Unit))
    }

    @AfterEach
    fun cleanUp() {
        val userIds = allEmails.flatMap { email ->
            jdbcTemplate.queryForList("SELECT id FROM users WHERE email = ?", Long::class.java, email)
        }
        val groupIds = userIds.flatMap { id ->
            jdbcTemplate.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, id)
        }.distinct()
        groupIds.forEach { groupId ->
            (AccountPurgeProvider.GROUP_FINANCIAL_DATA_IN_FK_ORDER + AccountPurgeProvider.GROUP_ACCESS_DATA)
                .forEach { jdbcTemplate.update("DELETE FROM ${it.name} WHERE ${it.condition}", groupId) }
        }
        userIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM group_invites WHERE inviter_user_id = ?", id)
            jdbcTemplate.update("UPDATE refresh_tokens SET replaced_by_id = NULL WHERE user_id = ?", id)
            AccountPurgeProvider.PERSONAL_DATA_IN_FK_ORDER
                .forEach { jdbcTemplate.update("DELETE FROM ${it.name} WHERE ${it.condition}", id) }
        }
        groupIds.forEach { jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", it) }
        allEmails.forEach { jdbcTemplate.update("DELETE FROM group_invites WHERE invitee_email = ?", it) }
        jdbcTemplate.update("DELETE FROM kiwify_webhook_events WHERE kiwify_event_id LIKE ?", "purge-test-%")
    }

    // ---- Coverage of the schema ----

    @Test
    fun `every table with a group_id column is covered by the purge`() {
        val tables = jdbcTemplate.queryForList(
            """SELECT table_name FROM information_schema.columns
               WHERE table_schema = DATABASE() AND column_name = 'group_id'""",
            String::class.java,
        ).map { it.lowercase() }.toSet()

        assertEquals(tables, AccountPurgeProvider.TABLES_WITH_GROUP_ID)
    }

    @Test
    fun `every table with a foreign key to users is covered by the purge`() {
        val tables = jdbcTemplate.queryForList(
            """SELECT DISTINCT table_name FROM information_schema.key_column_usage
               WHERE table_schema = DATABASE() AND referenced_table_name = 'users'""",
            String::class.java,
        ).map { it.lowercase() }.toSet()

        assertEquals(tables, AccountPurgeProvider.TABLES_REFERENCING_USERS)
    }

    @Test
    fun `every table with a foreign key to a purged table is purged too`() {
        val purgedTables = (
            AccountPurgeProvider.GROUP_FINANCIAL_DATA_IN_FK_ORDER +
                AccountPurgeProvider.GROUP_ACCESS_DATA +
                AccountPurgeProvider.PERSONAL_DATA_IN_FK_ORDER
            ).map { it.name }.toSet() + "groups"
        val referencing = jdbcTemplate.queryForList(
            """SELECT DISTINCT table_name, referenced_table_name FROM information_schema.key_column_usage
               WHERE table_schema = DATABASE() AND referenced_table_name IS NOT NULL""",
        ).filter { (it["referenced_table_name"] as String).lowercase() in purgedTables }
            .map { (it["table_name"] as String).lowercase() }
            .toSet()

        // A new child table (e.g. of purchase_items) left out would make the purge fail in production
        assertEquals(emptySet(), referencing - purgedTables)
    }

    @Test
    fun `the purge job is disabled in the test suite`() {
        assertTrue(applicationContext.getBeanNamesForType(AccountPurgeScheduler::class.java).isEmpty())
    }

    // ---- Sole member ----

    @Test
    fun `purging the only member deletes the group with every row that belongs to it`() {
        val groupId = insertGroup("Purge Sole Group")
        val userId = insertUser("Sole Member", soleEmail, groupId, due)
        insertFinancialData(groupId, "1111")
        insertGroupAccessData(groupId, userId)
        insertSessions(userId)
        val otherGroupId = insertGroup("Purge Untouched Group")
        val notDueId = insertUser("Not Due", notDueEmail, otherGroupId, notDue)
        insertFinancialData(otherGroupId, "2222")

        purgeDueAccountsUseCase.execute().getOrThrow()

        assertNoRowLeftFor(groupId)
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
        assertEquals(0, count("SELECT COUNT(*) FROM `groups` WHERE id = ?", groupId))
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", userId))
        assertEquals(0, count("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", userId))
        // Accounts not due yet, and other groups, are left alone
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", notDueId))
        assertEquals(1, count("SELECT COUNT(*) FROM payment_notifications WHERE group_id = ?", otherGroupId))
        assertEquals(1, count("SELECT COUNT(*) FROM purchase_items WHERE purchase_invoice_id IN (SELECT id FROM purchase_invoices WHERE group_id = ?)", otherGroupId))
    }

    // ---- Shared group ----

    @Test
    fun `purging a member of a shared group keeps the group and its data for the partner`() {
        val groupId = insertGroup("Purge Shared Group")
        val leavingId = insertUser("Leaving Member", leavingEmail, groupId, due)
        val stayingId = insertUser("Staying Member", stayingEmail, groupId, null)
        insertFinancialData(groupId, "3333")
        insertSessions(leavingId)
        insertSessions(stayingId)
        insertInvite(groupId, leavingId, "someone-else@controlai.test", "PENDING")
        insertInvite(groupId, leavingId, "accepted-guest@controlai.test", "ACCEPTED")
        val pendingToLeaving = insertInvite(groupId, stayingId, leavingEmail, "PENDING")
        val stayingInvite = insertInvite(groupId, stayingId, "friend@controlai.test", "PENDING")
        val snapshotBefore = groupSnapshot(groupId)

        val outcome = accountPurgeProvider.execute(leavingId).getOrThrow()

        assertEquals(PurgeOutcome.MEMBER_REMOVED, outcome)
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id = ?", leavingId))
        assertEquals(0, count("SELECT COUNT(*) FROM group_members WHERE user_id = ?", leavingId))
        assertEquals(0, count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", leavingId))
        assertEquals(0, count("SELECT COUNT(*) FROM password_reset_tokens WHERE user_id = ?", leavingId))
        assertEquals(0, count("SELECT COUNT(*) FROM group_invites WHERE inviter_user_id = ?", leavingId))
        assertEquals(0, count("SELECT COUNT(*) FROM group_invites WHERE id = ?", pendingToLeaving))
        // Cards, purchases, budget and holders stay with the partner, who keeps their session
        assertEquals(snapshotBefore, groupSnapshot(groupId))
        assertEquals(1, count("SELECT COUNT(*) FROM `groups` WHERE id = ?", groupId))
        assertEquals(1, count("SELECT COUNT(*) FROM group_members WHERE user_id = ?", stayingId))
        assertEquals(2, count("SELECT COUNT(*) FROM refresh_tokens WHERE user_id = ?", stayingId))
        assertEquals(1, count("SELECT COUNT(*) FROM group_invites WHERE id = ?", stayingInvite))
    }

    @Test
    fun `when both members are due, the second purge removes the group`() {
        val groupId = insertGroup("Purge Both Due Group")
        val firstId = insertUser("First Member", leavingEmail, groupId, Timestamp.from(due.toInstant().minusSeconds(60)))
        val secondId = insertUser("Second Member", stayingEmail, groupId, due)
        insertFinancialData(groupId, "4444")

        purgeDueAccountsUseCase.execute().getOrThrow()

        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE id IN (?, ?)", firstId, secondId))
        assertNoRowLeftFor(groupId)
        assertEquals(0, count("SELECT COUNT(*) FROM `groups` WHERE id = ?", groupId))
    }

    @Test
    fun `an account whose deletion was cancelled after being listed is not purged`() {
        val groupId = insertGroup("Purge Cancelled Group")
        val userId = insertUser("Cancelled Member", soleEmail, groupId, null)
        insertFinancialData(groupId, "5555")

        val outcome = accountPurgeProvider.execute(userId).getOrThrow()

        assertEquals(PurgeOutcome.NO_LONGER_DUE, outcome)
        assertEquals(1, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
        assertEquals(1, count("SELECT COUNT(*) FROM payment_notifications WHERE group_id = ?", groupId))
    }

    // ---- Kiwify ----

    @Test
    fun `the Kiwify payloads with the user's e-mail are redacted and still deduplicated by id`() {
        val groupId = insertGroup("Purge Kiwify Group")
        val userId = insertUser("Kiwify Buyer", soleEmail, groupId, due)
        // Kiwify sends "Customer" capitalised; the e-mail casing may differ from the stored one
        val capitalised = approvedPayload("purge-test-order-1", "Purge-Sole@ControlAI.test", customerKey = "Customer")
        val lowercase = approvedPayload("purge-test-order-2", soleEmail)
        insertKiwifyEvent("purge-test-order-1:compra_aprovada", capitalised)
        insertKiwifyEvent("purge-test-order-2:compra_aprovada", lowercase)
        val otherBuyer = approvedPayload("purge-test-order-3", "someone-else@controlai.test")
        insertKiwifyEvent("purge-test-order-3:compra_aprovada", otherBuyer)

        assertEquals(PurgeOutcome.SOLE_MEMBER_GROUP_PURGED, accountPurgeProvider.execute(userId).getOrThrow())

        assertEquals("""{"redacted": true}""", kiwifyPayload("purge-test-order-1:compra_aprovada"))
        assertEquals("""{"redacted": true}""", kiwifyPayload("purge-test-order-2:compra_aprovada"))
        assertTrue(kiwifyPayload("purge-test-order-3:compra_aprovada").contains("someone-else@controlai.test"))

        // Kiwify retrying the same event is still recognised as a duplicate: no account is recreated
        postKiwifyWebhook(capitalised)
        assertEquals(1, count("SELECT COUNT(*) FROM kiwify_webhook_events WHERE kiwify_event_id = ?", "purge-test-order-1:compra_aprovada"))
        assertEquals(0, count("SELECT COUNT(*) FROM users WHERE email = ?", soleEmail))
    }

    @Test
    fun `a new purchase with the same e-mail after the purge creates a fresh account without old data`() {
        val oldGroupId = insertGroup("Purge Returning Group")
        val oldUserId = insertUser("Returning Buyer", soleEmail, oldGroupId, due)
        insertFinancialData(oldGroupId, "6666")
        accountPurgeProvider.execute(oldUserId).getOrThrow()

        postKiwifyWebhook(approvedPayload("purge-test-order-new", soleEmail))

        val newUserId = jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, soleEmail)
        assertNotNull(newUserId)
        assertNotEquals(oldUserId, newUserId)
        val newGroupId = jdbcTemplate.queryForObject("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, newUserId)!!
        assertNotEquals(oldGroupId, newGroupId)
        assertEquals(1, count("SELECT COUNT(*) FROM subscriptions WHERE group_id = ? AND status = 'ACTIVE'", newGroupId))
        assertEquals(0, count("SELECT COUNT(*) FROM payment_notifications WHERE group_id = ?", newGroupId))
        assertEquals(0, count("SELECT COUNT(*) FROM payment_methods WHERE group_id = ?", newGroupId))
        assertEquals(0, count("SELECT COUNT(*) FROM budgets WHERE group_id = ?", newGroupId))
        assertEquals(0, count("SELECT COUNT(*) FROM purchase_invoices WHERE group_id = ?", newGroupId))
    }

    // ---- Helpers ----

    private fun assertNoRowLeftFor(groupId: Long) {
        val tablesWithGroupId = jdbcTemplate.queryForList(
            """SELECT table_name FROM information_schema.columns
               WHERE table_schema = DATABASE() AND column_name = 'group_id'""",
            String::class.java,
        )
        tablesWithGroupId.forEach { table ->
            assertEquals(0, count("SELECT COUNT(*) FROM $table WHERE group_id = ?", groupId), "rows left in $table")
        }
        // Child tables reach the group through their parents, which are gone: no orphan may remain
        CHILD_TABLES.forEach { (table, parentFk, parentTable) ->
            assertEquals(
                0,
                count("SELECT COUNT(*) FROM $table c WHERE NOT EXISTS (SELECT 1 FROM $parentTable p WHERE p.id = c.$parentFk)"),
                "orphan rows left in $table",
            )
        }
    }

    // Row counts of every financial table for the group, children included
    private fun groupSnapshot(groupId: Long): Map<String, Int> =
        AccountPurgeProvider.GROUP_FINANCIAL_DATA_IN_FK_ORDER.associate { table ->
            table.name to count("SELECT COUNT(*) FROM ${table.name} WHERE ${table.condition}", groupId)
        }

    private fun count(sql: String, vararg args: Any): Int = jdbcTemplate.queryForObject(sql, Int::class.java, *args)!!

    private fun insertGroup(name: String): Long {
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", name)
        return lastInsertId()
    }

    private fun insertUser(name: String, email: String, groupId: Long, deletionScheduledFor: Timestamp?): Long {
        jdbcTemplate.update(
            "INSERT INTO users (name, email, deletion_requested_at, deletion_scheduled_for) VALUES (?, ?, ?, ?)",
            name,
            email,
            deletionScheduledFor?.let { Timestamp.from(it.toInstant().minus(Duration.ofDays(30))) },
            deletionScheduledFor,
        )
        val userId = lastInsertId()
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", groupId, userId)
        return userId
    }

    // One row in every financial table: card with sub-card, purchase with NFC-e (items and
    // payments), installments, category and a budget with items, income and payment period
    private fun insertFinancialData(groupId: Long, lastDigits: String) {
        jdbcTemplate.update("INSERT INTO holders (group_id, name) VALUES (?, ?)", groupId, "Holder $lastDigits")
        val holderId = lastInsertId()
        jdbcTemplate.update(
            "INSERT INTO payment_methods (group_id, name, type, holder_id) VALUES (?, 'Cartão', 'CREDIT_CARD', ?)",
            groupId,
            holderId,
        )
        val paymentMethodId = lastInsertId()
        jdbcTemplate.update(
            "INSERT INTO sub_cards (payment_method_id, last_four_digits, type) VALUES (?, ?, 'PHYSICAL')",
            paymentMethodId,
            lastDigits,
        )
        val subCardId = lastInsertId()
        jdbcTemplate.update("INSERT INTO categories (group_id, name) VALUES (?, ?)", groupId, "Purge Category $lastDigits")
        val categoryId = lastInsertId()

        jdbcTemplate.update(
            "INSERT INTO purchase_invoices (group_id, merchant_name, access_key, total, category_id) VALUES (?, 'Mercado', ?, 10.00, ?)",
            groupId,
            UUID.randomUUID().toString().replace("-", "").take(44),
            categoryId,
        )
        val invoiceId = lastInsertId()
        jdbcTemplate.update(
            "INSERT INTO purchase_items (product_name, code, quantity, unit, unit_price, total_price, purchase_invoice_id) VALUES ('Arroz', '789', 1, 'UN', 10.00, 10.00, ?)",
            invoiceId,
        )
        jdbcTemplate.update("INSERT INTO purchase_payments (type, value, purchase_invoices_id) VALUES ('CREDIT', 10.00, ?)", invoiceId)
        jdbcTemplate.update(
            """INSERT INTO payment_notifications (group_id, card_last_digits, purchased_at, amount, merchant_name,
                   number_of_installments, origin, origin_type, category_id, payment_method_id, sub_card_id, purchase_invoice_id)
               VALUES (?, ?, NOW(), 20.00, 'Loja', 2, 'MANUAL', 'MANUAL', ?, ?, ?, ?)""",
            groupId,
            lastDigits,
            categoryId,
            paymentMethodId,
            subCardId,
            invoiceId,
        )
        val notificationId = lastInsertId()
        listOf(1, 2).forEach { number ->
            jdbcTemplate.update(
                "INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date) VALUES (?, ?, ?, 2, 10.00, CURDATE())",
                groupId,
                notificationId,
                number,
            )
        }

        jdbcTemplate.update("INSERT INTO budgets (group_id, reference_month) VALUES (?, '2031-01')", groupId)
        val budgetId = lastInsertId()
        jdbcTemplate.update("INSERT INTO budget_items (budget_id, category_id, type, expected) VALUES (?, ?, 'EXPENSE', 100)", budgetId, categoryId)
        jdbcTemplate.update("INSERT INTO budget_incomes (budget_id, label, amount) VALUES (?, 'Salário', 1000)", budgetId)
        jdbcTemplate.update(
            "INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date) VALUES (?, ?, '2031-01-01', '2031-01-31')",
            budgetId,
            paymentMethodId,
        )
    }

    private fun insertGroupAccessData(groupId: Long, userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO api_keys (group_id, key_hash, label) VALUES (?, ?, 'Purge Test Key')",
            groupId,
            UUID.randomUUID().toString().replace("-", "").padEnd(64, '0'),
        )
        jdbcTemplate.update("INSERT INTO subscriptions (group_id, plan, status) VALUES (?, 'ANNUAL', 'ACTIVE')", groupId)
        insertInvite(groupId, userId, "guest@controlai.test", "DECLINED")
    }

    // Two chained refresh tokens (rotation) and a password reset token
    private fun insertSessions(userId: Long) {
        jdbcTemplate.update(
            "INSERT INTO refresh_tokens (user_id, token_hash, expires_at, absolute_expires_at) VALUES (?, ?, NOW() + INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY)",
            userId,
            randomHash(),
        )
        val newerId = lastInsertId()
        jdbcTemplate.update(
            "INSERT INTO refresh_tokens (user_id, token_hash, expires_at, absolute_expires_at, revoked_at, replaced_by_id) VALUES (?, ?, NOW(), NOW() + INTERVAL 30 DAY, NOW(), ?)",
            userId,
            randomHash(),
            newerId,
        )
        jdbcTemplate.update(
            "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at) VALUES (?, ?, NOW() + INTERVAL 1 HOUR)",
            userId,
            randomHash(),
        )
    }

    private fun insertInvite(groupId: Long, inviterId: Long, inviteeEmail: String, status: String): Long {
        jdbcTemplate.update(
            "INSERT INTO group_invites (group_id, inviter_user_id, invitee_email, status, token, expires_at) VALUES (?, ?, ?, ?, ?, NOW() + INTERVAL 7 DAY)",
            groupId,
            inviterId,
            inviteeEmail,
            status,
            UUID.randomUUID().toString(),
        )
        return lastInsertId()
    }

    private fun insertKiwifyEvent(eventId: String, payload: String) {
        jdbcTemplate.update(
            "INSERT INTO kiwify_webhook_events (kiwify_event_id, order_status, raw_payload, processed_at) VALUES (?, 'compra_aprovada', ?, NOW())",
            eventId,
            payload,
        )
    }

    private fun kiwifyPayload(eventId: String): String =
        jdbcTemplate.queryForObject("SELECT raw_payload FROM kiwify_webhook_events WHERE kiwify_event_id = ?", String::class.java, eventId)!!

    private fun approvedPayload(orderId: String, email: String, customerKey: String = "customer") =
        """{"order_id":"$orderId","order_status":"compra_aprovada","$customerKey":{"email":"$email","full_name":"Returning Buyer"},"product":{"product_id":"test-annual-product-id"}}"""

    private fun postKiwifyWebhook(payload: String) {
        mockMvc.perform(
            post("/webhooks/kiwify")
                .param("token", "test-kiwify-webhook-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload),
        ).andExpect(status().isOk)
    }

    private fun randomHash() = UUID.randomUUID().toString().replace("-", "").padEnd(64, 'a')

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    companion object {
        // (child table, FK column, parent table) for the tables without group_id
        private val CHILD_TABLES = listOf(
            Triple("purchase_payments", "purchase_invoices_id", "purchase_invoices"),
            Triple("purchase_items", "purchase_invoice_id", "purchase_invoices"),
            Triple("budget_payment_periods", "budget_id", "budgets"),
            Triple("budget_items", "budget_id", "budgets"),
            Triple("budget_incomes", "budget_id", "budgets"),
            Triple("sub_cards", "payment_method_id", "payment_methods"),
        )
    }
}
