package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.domain.budget.gateway.EnsureFutureBudgetGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Isolated in its own class (separate Spring context permutation, EnsureFutureBudgetGateway
// mocked) so this failure-path scenario doesn't interfere with the happy-path tests in
// SavePaymentNotificationProviderTest, which rely on the real gateway.
@SpringBootTest
class SavePaymentNotificationProviderRollbackTest {

    @Autowired private lateinit var savePaymentNotificationProvider: SavePaymentNotificationProvider
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @MockitoBean private lateinit var ensureFutureBudgetGateway: EnsureFutureBudgetGateway

    private val groupId = 1L

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL, payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.update("DELETE FROM installments")
        jdbcTemplate.update("DELETE FROM payment_notifications")
    }

    private fun countNotificationsByMerchant(merchantName: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment_notifications WHERE merchant_name = ?",
            Int::class.java, merchantName,
        )!!

    @Test
    fun `should roll back the whole notification when installment budget creation fails, since SQS has no outer transaction`() {
        // No paymentMethodId set, so createInstallments() falls back to the calendar-month cycle:
        // the 1st (and first-checked) installment due date lands in the purchase month itself.
        `when`(ensureFutureBudgetGateway.execute(groupId, YearMonth.of(2026, 1)))
            .thenReturn(Result.failure(IllegalStateException("budget creation failed")))

        val notification = PaymentNotification(
            groupId = groupId,
            purchasedAt = LocalDateTime.of(2026, 1, 15, 10, 0).toInstant(ZoneOffset.UTC),
            amount = BigDecimal("300.00"),
            merchantName = "Rollback Test Merchant",
            numberOfInstallments = 3,
            origin = "NUBANK",
            originType = "SMS",
        )

        val result = savePaymentNotificationProvider.execute(notification)

        assertTrue(result.isFailure)
        assertEquals(0, countNotificationsByMerchant("Rollback Test Merchant"))
    }
}
