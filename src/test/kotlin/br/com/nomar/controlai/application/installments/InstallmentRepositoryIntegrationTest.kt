package br.com.nomar.controlai.application.installments

import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest
class InstallmentRepositoryIntegrationTest {

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
               (purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, group_id)
               VALUES (CURRENT_TIMESTAMP, 100.00, 'Test Store', 3, 'MANUAL', 'MANUAL', 1)"""
        )
        parentId = jdbcTemplate.queryForObject(
            "SELECT id FROM payment_notifications WHERE merchant_name = 'Test Store'",
            Long::class.java
        )!!
    }

    @Test
    fun `should save and find installments by parentId ordered by number`() {
        val installments = listOf(
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 2, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 6, 15)),
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 1, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 5, 15)),
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 3, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 7, 15)),
        )
        installmentRepository.saveAll(installments)

        val result = installmentRepository.findByParentIdOrderByInstallmentNumber(parentId)
        assertEquals(3, result.size)
        assertEquals(1, result[0].installmentNumber)
        assertEquals(2, result[1].installmentNumber)
        assertEquals(3, result[2].installmentNumber)
    }

    @Test
    fun `should find only non-cancelled installments`() {
        val installments = listOf(
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 1, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 5, 15)),
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 2, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 6, 15), cancelledAt = LocalDateTime.now()),
            Installment(groupId = 1L, parentId = parentId, installmentNumber = 3, totalInstallments = 3, amount = BigDecimal("100.00"), dueDate = LocalDate.of(2026, 7, 15)),
        )
        installmentRepository.saveAll(installments)

        val result = installmentRepository.findByParentIdAndCancelledAtIsNull(parentId)
        assertEquals(2, result.size)
    }

    @Test
    fun `InstallmentResponse from() should map correctly`() {
        val installment = Installment(
            id = 42,
            parentId = 1,
            installmentNumber = 3,
            totalInstallments = 10,
            amount = BigDecimal("589.90"),
            dueDate = LocalDate.of(2026, 7, 15),
            cancelledAt = null,
        )

        val response = InstallmentResponse.from(installment)
        assertEquals(42, response.id)
        assertEquals(3, response.installmentNumber)
        assertEquals(10, response.totalInstallments)
        assertEquals(BigDecimal("589.90"), response.amount)
        assertFalse(response.cancelled)
    }

    @Test
    fun `InstallmentResponse from() should set cancelled true when cancelledAt is set`() {
        val installment = Installment(
            id = 1,
            parentId = 1,
            installmentNumber = 1,
            totalInstallments = 3,
            amount = BigDecimal("100.00"),
            dueDate = LocalDate.of(2026, 5, 15),
            cancelledAt = LocalDateTime.now(),
        )

        val response = InstallmentResponse.from(installment)
        assertTrue(response.cancelled)
    }
}
