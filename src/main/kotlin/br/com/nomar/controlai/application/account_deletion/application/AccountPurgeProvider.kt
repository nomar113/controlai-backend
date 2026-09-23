package br.com.nomar.controlai.application.account_deletion.application

import br.com.nomar.controlai.domain.account_deletion.entity.PurgeOutcome
import br.com.nomar.controlai.domain.account_deletion.gateway.PurgeAccountGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
import java.time.Clock

// Plain SQL instead of JPA repositories (Tech Spec deviation): every FK is RESTRICT, so the
// delete order is explicit here, where it can be read and tested in one place.
@Component
class AccountPurgeProvider(
    private val jdbcTemplate: JdbcTemplate,
    private val transactionTemplate: TransactionTemplate,
    private val clock: Clock,
) : PurgeAccountGateway {

    // TransactionTemplate instead of @Transactional: runCatching would swallow the
    // exception before the proxy could mark the transaction for rollback
    override fun execute(userId: Long): Result<PurgeOutcome> {
        return runCatching {
            transactionTemplate.execute { purge(userId) }!!
        }
    }

    private fun purge(userId: Long): PurgeOutcome {
        // Locks the user row: a cancellation racing with the job either lands before (and the
        // account is skipped) or waits for the purge to finish
        val email = findEmailIfDeletionDue(userId) ?: return PurgeOutcome.NO_LONGER_DUE
        val groupId = findGroupId(userId)
        val soleMember = groupId != null && isSoleMember(groupId)

        if (soleMember) {
            deleteGroupFinancialData(groupId!!)
            deleteGroupAccessData(groupId)
        }
        deleteInvitesSentOrReceived(userId, email)
        deletePersonalData(userId)
        if (soleMember) deleteGroup(groupId!!)
        redactKiwifyWebhookEvents(email)

        return if (soleMember) PurgeOutcome.SOLE_MEMBER_GROUP_PURGED else PurgeOutcome.MEMBER_REMOVED
    }

    private fun findEmailIfDeletionDue(userId: Long): String? =
        jdbcTemplate.queryForList(
            "SELECT email FROM users WHERE id = ? AND deletion_scheduled_for <= ? FOR UPDATE",
            String::class.java,
            userId,
            Timestamp.from(clock.instant()),
        ).firstOrNull()

    private fun findGroupId(userId: Long): Long? =
        jdbcTemplate.queryForList("SELECT group_id FROM group_members WHERE user_id = ?", Long::class.java, userId)
            .firstOrNull()

    // FOR UPDATE also blocks a new member from joining the group while it is being deleted
    private fun isSoleMember(groupId: Long): Boolean =
        jdbcTemplate.queryForList("SELECT user_id FROM group_members WHERE group_id = ? FOR UPDATE", Long::class.java, groupId)
            .size == 1

    private fun deleteGroupFinancialData(groupId: Long) {
        GROUP_FINANCIAL_DATA_IN_FK_ORDER.forEach { delete(it, groupId) }
    }

    private fun deleteGroupAccessData(groupId: Long) {
        GROUP_ACCESS_DATA.forEach { delete(it, groupId) }
    }

    // Every invite the user sent (any status, they reference users) and the pending ones sent
    // to their e-mail; answered invites to them hold no reference and belong to the other group
    private fun deleteInvitesSentOrReceived(userId: Long, email: String) {
        jdbcTemplate.update("DELETE FROM group_invites WHERE inviter_user_id = ?", userId)
        jdbcTemplate.update("DELETE FROM group_invites WHERE invitee_email = ? AND status = 'PENDING'", email)
    }

    private fun deletePersonalData(userId: Long) {
        // refresh_tokens.replaced_by_id points to rows of the same user: InnoDB checks FKs row by
        // row, so the chain is cut before the tokens are deleted
        jdbcTemplate.update("UPDATE refresh_tokens SET replaced_by_id = NULL WHERE user_id = ?", userId)
        PERSONAL_DATA_IN_FK_ORDER.forEach { delete(it, userId) }
    }

    private fun deleteGroup(groupId: Long) {
        jdbcTemplate.update("DELETE FROM `groups` WHERE id = ?", groupId)
    }

    // The event log keeps kiwify_event_id (the idempotency key) but drops the buyer's data.
    // Kiwify sends "Customer" capitalised, the parser accepts any casing, and MySQL JSON paths
    // are case-sensitive, hence both keys.
    private fun redactKiwifyWebhookEvents(email: String) {
        jdbcTemplate.update(
            """
            UPDATE kiwify_webhook_events SET raw_payload = JSON_OBJECT('redacted', true)
            WHERE LOWER(TRIM(COALESCE(raw_payload->>'$.customer.email', raw_payload->>'$.Customer.email'))) = ?
            """.trimIndent(),
            email.trim().lowercase(),
        )
    }

    private fun delete(table: PurgeTable, id: Long) {
        jdbcTemplate.update("DELETE FROM ${table.name} WHERE ${table.condition}", id)
    }

    // A table the purge deletes from, and the condition (one `?`) that selects the rows of a
    // group or user. Tables without group_id reach the group through their parent.
    data class PurgeTable(val name: String, val condition: String)

    companion object {
        private const val BY_GROUP_ID = "group_id = ?"
        private const val BY_USER_ID = "user_id = ?"
        private const val BY_GROUP_PURCHASE = "IN (SELECT id FROM purchase_invoices WHERE group_id = ?)"
        private const val BY_GROUP_BUDGET = "budget_id IN (SELECT id FROM budgets WHERE group_id = ?)"

        private val PURCHASE_TABLES = listOf(
            PurgeTable("installments", BY_GROUP_ID),
            PurgeTable("payment_notifications", BY_GROUP_ID),
            PurgeTable("purchase_payments", "purchase_invoices_id $BY_GROUP_PURCHASE"),
            PurgeTable("purchase_items", "purchase_invoice_id $BY_GROUP_PURCHASE"),
            PurgeTable("purchase_invoices", BY_GROUP_ID),
        )

        private val BUDGET_TABLES = listOf(
            PurgeTable("budget_payment_periods", BY_GROUP_BUDGET),
            PurgeTable("budget_items", BY_GROUP_BUDGET),
            PurgeTable("budget_incomes", BY_GROUP_BUDGET),
            PurgeTable("budgets", BY_GROUP_ID),
        )

        private val CARD_TABLES = listOf(
            PurgeTable("sub_cards", "payment_method_id IN (SELECT id FROM payment_methods WHERE group_id = ?)"),
            PurgeTable("payment_methods", BY_GROUP_ID),
            PurgeTable("holders", BY_GROUP_ID),
        )

        // Children before parents (Tech Spec, "Ordem da purga"). Also the order the test
        // suite's TestDatabaseCleaner wipes business data in.
        val GROUP_FINANCIAL_DATA_IN_FK_ORDER: List<PurgeTable> =
            PURCHASE_TABLES + BUDGET_TABLES + CARD_TABLES + PurgeTable("categories", BY_GROUP_ID)

        // What gives access to the group: the Shortcut's API keys, its invites, its subscription
        val GROUP_ACCESS_DATA: List<PurgeTable> = listOf(
            PurgeTable("api_keys", BY_GROUP_ID),
            PurgeTable("group_invites", BY_GROUP_ID),
            PurgeTable("subscriptions", BY_GROUP_ID),
        )

        // password_reset_tokens already cascades from users; deleting it here keeps the purge
        // independent of that FK option
        val PERSONAL_DATA_IN_FK_ORDER: List<PurgeTable> = listOf(
            PurgeTable("group_members", BY_USER_ID),
            PurgeTable("refresh_tokens", BY_USER_ID),
            PurgeTable("password_reset_tokens", BY_USER_ID),
            PurgeTable("users", "id = ?"),
        )

        // Every table with a group_id column the purge accounts for. The coverage test compares
        // it with information_schema, so a new tenant table cannot be left out unnoticed.
        val TABLES_WITH_GROUP_ID: Set<String> =
            (GROUP_FINANCIAL_DATA_IN_FK_ORDER + GROUP_ACCESS_DATA + PERSONAL_DATA_IN_FK_ORDER)
                .filter { it.condition == BY_GROUP_ID }
                .map { it.name }
                .toSet() + "group_members"

        // Every table with a FK to users the purge accounts for (group_invites via inviter_user_id)
        val TABLES_REFERENCING_USERS: Set<String> =
            PERSONAL_DATA_IN_FK_ORDER.map { it.name }.toSet() - "users" + "group_invites"
    }
}
