package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.account_deletion.application.AccountPurgeProvider
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

// Single place where integration tests wipe business data. Picked up by component scan in test
// runs only, like TestAuthMockMvcConfig. Tests that only remove their own rows (filtered by
// group_id, e-mail, key hash...) keep doing so locally; unscoped deletes belong here.
@Component
class TestDatabaseCleaner(private val jdbcTemplate: JdbcTemplate) {

    // Deletes every group's financial data (purchases, cards, holders, budgets, categories),
    // including the rows seeded by migrations (e.g. the store reviewer's demo data, V40/V42).
    // Groups, users, memberships, subscriptions and API keys are kept, so the seeded group 1
    // and its active subscription remain usable by the tests.
    fun deleteFinancialData() {
        FINANCIAL_TABLES_IN_FK_ORDER.forEach { table -> jdbcTemplate.update("DELETE FROM $table") }
    }

    companion object {
        // Children before parents, so no FK (all RESTRICT) blocks a delete: the same list and
        // order the account purge uses (Tarefa 5.0), so both stay in sync with the schema
        val FINANCIAL_TABLES_IN_FK_ORDER: List<String> =
            AccountPurgeProvider.GROUP_FINANCIAL_DATA_IN_FK_ORDER.map { it.name }
    }
}
