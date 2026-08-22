package br.com.nomar.controlai.application.installments

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
class InstallmentControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var parentId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (CURRENT_TIMESTAMP, 100.00, 'Test Store', 5, 'MANUAL', 'MANUAL', 1)"""
        )
        parentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Test Store'",
            Long::class.java
        )!!
    }

    private fun insertInstallment(number: Int, dueDate: LocalDate, amount: Double = 100.0) {
        jdbcTemplate.update(
            """INSERT INTO installments (parent_id, installment_number, total_installments, amount, due_date, group_id)
               VALUES (?, ?, 5, ?, ?, 1)""",
            parentId, number, amount, dueDate.toString()
        )
    }

    @Test
    fun `GET by parentId should return installments ordered by number`() {
        insertInstallment(3, LocalDate.of(2026, 7, 15))
        insertInstallment(1, LocalDate.of(2026, 5, 15))
        insertInstallment(2, LocalDate.of(2026, 6, 15))

        mockMvc.perform(get("/installments?parentId=$parentId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].installmentNumber").value(1))
            .andExpect(jsonPath("$[1].installmentNumber").value(2))
            .andExpect(jsonPath("$[2].installmentNumber").value(3))
    }

    @Test
    fun `GET projection should return monthly totals`() {
        val today = LocalDate.now()
        val month1 = today.withDayOfMonth(15)
        val month2 = today.plusMonths(1).withDayOfMonth(15)

        insertInstallment(1, month1, 200.0)
        insertInstallment(2, month1, 300.0)
        insertInstallment(3, month2, 150.0)

        mockMvc.perform(get("/installments/projection"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].total").value(500.0))
            .andExpect(jsonPath("$[0].count").value(2))
            .andExpect(jsonPath("$[1].total").value(150.0))
            .andExpect(jsonPath("$[1].count").value(1))
    }

    @Test
    fun `DELETE future should cancel only future installments`() {
        val pastDate = LocalDate.now().minusDays(10)
        val futureDate1 = LocalDate.now().plusDays(10)
        val futureDate2 = LocalDate.now().plusDays(40)

        insertInstallment(1, pastDate)
        insertInstallment(2, futureDate1)
        insertInstallment(3, futureDate2)

        mockMvc.perform(delete("/installments/future?parentId=$parentId"))
            .andExpect(status().isNoContent)

        val cancelledCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM installments WHERE parent_id = ? AND cancelled_at IS NOT NULL",
            Int::class.java, parentId
        )
        val activeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM installments WHERE parent_id = ? AND cancelled_at IS NULL",
            Int::class.java, parentId
        )
        assertEquals(2, cancelledCount, "Should cancel 2 future installments")
        assertEquals(1, activeCount, "Should keep 1 past installment active")
    }

    @Test
    fun `PATCH should update amount of future installment`() {
        val futureDate = LocalDate.now().plusDays(30)
        insertInstallment(1, futureDate, 100.0)

        val installmentId = jdbcTemplate.queryForObject(
            "SELECT id FROM installments WHERE parent_id = ? AND installment_number = 1",
            Long::class.java, parentId
        )!!

        mockMvc.perform(
            patch("/installments/$installmentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 150.00}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.amount").value(150.0))
    }

    @Test
    fun `PATCH past installment should return 400`() {
        val pastDate = LocalDate.now().minusDays(10)
        insertInstallment(1, pastDate, 100.0)

        val installmentId = jdbcTemplate.queryForObject(
            "SELECT id FROM installments WHERE parent_id = ? AND installment_number = 1",
            Long::class.java, parentId
        )!!

        mockMvc.perform(
            patch("/installments/$installmentId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"amount": 150.00}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST preview with a credit card paymentMethodId should honor its closing day, not plain calendar months`() {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Preview Holder', 1)")
        val holderId = jdbcTemplate.queryForObject(
            "SELECT id FROM holders WHERE name = 'Preview Holder'", Long::class.java
        )!!
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id, closing_day) VALUES ('Preview Card', 'CREDIT_CARD', ?, 1, 1)",
            holderId,
        )
        val paymentMethodId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_methods WHERE name = 'Preview Card'", Long::class.java
        )!!

        try {
            // Card closes on day 1: a purchase on the 20th falls in the cycle that closes the
            // following month, so the first installment is due next month, not this one — the
            // same rule SavePaymentNotificationProvider applies when it actually persists the
            // installments (see BudgetPeriodCalculator.resolveInstallmentDueDate).
            mockMvc.perform(
                post("/installments/preview")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "totalAmount": 300.00,
                            "numberOfInstallments": 3,
                            "startDate": "2026-08-20",
                            "paymentMethodId": $paymentMethodId
                        }
                        """.trimIndent()
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$[0].dueDate").value("2026-09-20"))
                .andExpect(jsonPath("$[1].dueDate").value("2026-10-20"))
                .andExpect(jsonPath("$[2].dueDate").value("2026-11-20"))
        } finally {
            jdbcTemplate.update("DELETE FROM payment_methods WHERE id = ?", paymentMethodId)
            jdbcTemplate.update("DELETE FROM holders WHERE id = ?", holderId)
        }
    }

    @Test
    fun `POST preview without paymentMethodId should keep the plain calendar-month fallback`() {
        mockMvc.perform(
            post("/installments/preview")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                        "totalAmount": 300.00,
                        "numberOfInstallments": 3,
                        "startDate": "2026-08-20"
                    }
                    """.trimIndent()
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].dueDate").value("2026-08-20"))
            .andExpect(jsonPath("$[1].dueDate").value("2026-09-20"))
            .andExpect(jsonPath("$[2].dueDate").value("2026-10-20"))
    }

    @Test
    fun `POST reconcile should recalculate due dates after a card period was manually corrected`() {
        jdbcTemplate.update("INSERT INTO holders (name, group_id) VALUES ('Reconcile Holder', 1)")
        val holderId = jdbcTemplate.queryForObject(
            "SELECT id FROM holders WHERE name = 'Reconcile Holder'", Long::class.java
        )!!
        jdbcTemplate.update(
            "INSERT INTO payment_methods (name, type, holder_id, group_id, closing_day) VALUES ('LATAM Pass', 'CREDIT_CARD', ?, 1, 1)",
            holderId,
        )
        val paymentMethodId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_methods WHERE name = 'LATAM Pass'", Long::class.java
        )!!

        jdbcTemplate.update("INSERT INTO budgets (reference_month, group_id) VALUES ('2026-08', 1)")
        val budgetId = jdbcTemplate.queryForObject(
            "SELECT id FROM budgets WHERE reference_month = '2026-08'", Long::class.java
        )!!
        // Raw, uncorrected closingDay=1 period as auto-generated by EnsureFutureBudgetProvider.
        jdbcTemplate.update(
            """INSERT INTO budget_payment_periods (budget_id, payment_method_id, start_date, end_date)
               VALUES (?, ?, '2026-07-02', '2026-08-01')""",
            budgetId, paymentMethodId,
        )

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id, payment_method_id)
               VALUES ('2026-08-07 10:00:00', 1197.11, 'Venvanse', 3, 'MANUAL', 'MANUAL', 1, ?)""",
            paymentMethodId,
        )
        val purchaseId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Venvanse'", Long::class.java
        )!!
        // Installments baked in against the raw period above -> 1st installment wrongly pushed to September.
        listOf(1 to "2026-09-07", 2 to "2026-10-07", 3 to "2026-11-07").forEach { (number, dueDate) ->
            jdbcTemplate.update(
                """INSERT INTO installments (group_id, parent_id, installment_number, total_installments, amount, due_date)
                   VALUES (1, ?, ?, 3, 399.04, ?)""",
                purchaseId, number, dueDate,
            )
        }

        try {
            // Manual correction, mirroring what PUT /budgets/{id}/periods persists.
            jdbcTemplate.update(
                "UPDATE budget_payment_periods SET start_date = '2026-07-31', end_date = '2026-08-31' WHERE budget_id = ? AND payment_method_id = ?",
                budgetId, paymentMethodId,
            )

            mockMvc.perform(post("/installments/reconcile"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.recalculated").value(3))

            val installments = jdbcTemplate.queryForList(
                "SELECT installment_number, due_date FROM installments WHERE parent_id = ? ORDER BY installment_number",
                purchaseId,
            )
            assertEquals(LocalDate.of(2026, 8, 7), (installments[0]["due_date"] as java.sql.Date).toLocalDate())
            assertEquals(LocalDate.of(2026, 9, 7), (installments[1]["due_date"] as java.sql.Date).toLocalDate())
            assertEquals(LocalDate.of(2026, 10, 7), (installments[2]["due_date"] as java.sql.Date).toLocalDate())
        } finally {
            // Recalculation spans August-October and auto-creates budgets for months that didn't
            // exist yet (EnsureFutureBudgetProvider). A leftover budget for this payment method's
            // charge months would let a later test's ensurePaymentPeriodsSynced self-heal a period
            // into it for an unrelated payment method, so wipe budgets entirely here rather than
            // scoping the cleanup — same approach as InstallmentReconciliationRunnerIT's tearDown.
            jdbcTemplate.update("DELETE FROM installments WHERE parent_id = ?", purchaseId)
            jdbcTemplate.update("DELETE FROM payment_notifications WHERE id = ?", purchaseId)
            jdbcTemplate.update("DELETE FROM budget_payment_periods")
            jdbcTemplate.update("DELETE FROM budgets")
            jdbcTemplate.update("DELETE FROM payment_methods WHERE id = ?", paymentMethodId)
            jdbcTemplate.update("DELETE FROM holders WHERE id = ?", holderId)
        }
    }
}
