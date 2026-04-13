package br.com.nomar.controlai.application.installments

import br.com.nomar.controlai.application.installments.application.CreateInstallmentsProvider
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate

@SpringBootTest
class CreateInstallmentsProviderTest {

    @Autowired private lateinit var createInstallmentsProvider: CreateInstallmentsProvider
    @Autowired private lateinit var installmentRepository: InstallmentRepository
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    private var parentId: Long = 0

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type)
               VALUES (CURRENT_TIMESTAMP, 589.90, 'Apple Store', 10, 'MANUAL', 'MANUAL')"""
        )
        parentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Apple Store'",
            Long::class.java
        )!!
    }

    @Test
    fun `should create N installments with correct sequential numbers`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            totalInstallments = 3,
            amount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 5, 15),
        )

        assertEquals(3, result.size)
        assertEquals(1, result[0].installmentNumber)
        assertEquals(2, result[1].installmentNumber)
        assertEquals(3, result[2].installmentNumber)
        result.forEach {
            assertEquals(3, it.totalInstallments)
            assertEquals(BigDecimal("100.00"), it.amount)
            assertEquals(parentId, it.parentId)
        }
    }

    @Test
    fun `should calculate dates as same day next month`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            totalInstallments = 3,
            amount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 1, 15),
        )

        assertEquals(LocalDate.of(2026, 1, 15), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 15), result[1].dueDate)
        assertEquals(LocalDate.of(2026, 3, 15), result[2].dueDate)
    }

    @Test
    fun `should handle day 31 in short months`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            totalInstallments = 3,
            amount = BigDecimal("100.00"),
            startDate = LocalDate.of(2026, 1, 31),
        )

        assertEquals(LocalDate.of(2026, 1, 31), result[0].dueDate)
        assertEquals(LocalDate.of(2026, 2, 28), result[1].dueDate) // Feb 2026 has 28 days
        assertEquals(LocalDate.of(2026, 3, 31), result[2].dueDate)
    }

    @Test
    fun `should handle day 29 in leap year February`() {
        // 2028 is a leap year
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            totalInstallments = 2,
            amount = BigDecimal("50.00"),
            startDate = LocalDate.of(2028, 1, 29),
        )

        assertEquals(LocalDate.of(2028, 1, 29), result[0].dueDate)
        assertEquals(LocalDate.of(2028, 2, 29), result[1].dueDate) // Leap year
    }

    @Test
    fun `should create single installment`() {
        val result = createInstallmentsProvider.execute(
            parentId = parentId,
            totalInstallments = 1,
            amount = BigDecimal("900.00"),
            startDate = LocalDate.of(2026, 5, 10),
        )

        assertEquals(1, result.size)
        assertEquals(1, result[0].installmentNumber)
        assertEquals(1, result[0].totalInstallments)
        assertEquals(LocalDate.of(2026, 5, 10), result[0].dueDate)
    }

    @Test
    fun `calculateDueDate should handle day 30 in February`() {
        val dueDate = CreateInstallmentsProvider.calculateDueDate(LocalDate.of(2026, 1, 30), 2)
        assertEquals(LocalDate.of(2026, 2, 28), dueDate)
    }
}
