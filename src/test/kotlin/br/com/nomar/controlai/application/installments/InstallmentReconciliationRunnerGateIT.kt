package br.com.nomar.controlai.application.installments

import br.com.nomar.controlai.application.installments.application.InstallmentReconciliationRunner
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.jdbc.core.JdbcTemplate

/**
 * Verifies InstallmentReconciliationRunner stays off during a normal boot: the property
 * `app.reconciliation.installments.run-full-backfill` is absent/false in the test classpath's
 * application.properties, so `@ConditionalOnProperty` must keep the bean out of the context.
 */
@SpringBootTest
class InstallmentReconciliationRunnerGateIT {

    @Autowired private lateinit var context: ApplicationContext
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var missingParentId: Long = 0

    @BeforeEach
    fun setUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")

        // Purchase without any statement, exactly what the backfill would pick up if it ran.
        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES ('2026-01-05 10:00:00', 100.00, 'Loja Gate', 1, 'MANUAL', 'MANUAL', 1)""",
        )
        missingParentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Loja Gate'", Long::class.java
        )!!
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    @Test
    fun `should not register the reconciliation runner bean when the backfill property is off by default`() {
        assertTrue(context.getBeanNamesForType(InstallmentReconciliationRunner::class.java).isEmpty())
    }

    @Test
    fun `should not create any statement on a normal boot with the property off`() {
        val count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM installments WHERE parent_id = ?", Int::class.java, missingParentId,
        )
        assertEquals(0, count)
    }
}
