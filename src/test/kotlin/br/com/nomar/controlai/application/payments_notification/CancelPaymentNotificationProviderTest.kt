package br.com.nomar.controlai.application.payments_notification

import br.com.nomar.controlai.application.payments_notification.application.CancelPaymentNotificationProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.config.TestSecurityContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@SpringBootTest
class CancelPaymentNotificationProviderTest {

    @Autowired
    private lateinit var provider: CancelPaymentNotificationProvider

    @Autowired
    private lateinit var repository: PaymentNotificationRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun authenticate() = TestSecurityContext.authenticateAsGroup()

    @AfterEach
    fun clearAuth() = TestSecurityContext.clear()

    private fun createNotification(cancelledAt: LocalDateTime? = null): PaymentNotification {
        return repository.save(
            PaymentNotification(
                purchasedAt = LocalDateTime.of(2024, 1, 15, 10, 0).toInstant(ZoneOffset.UTC),
                amount = BigDecimal("99.90"),
                merchantName = "Loja Teste Cancel",
                numberOfInstallments = 1,
                origin = "NUBANK",
                originType = "HTTP_REQUEST",
                cancelledAt = cancelledAt,
            )
        )
    }

    @Test
    fun `should cancel notification successfully`() {
        val notification = createNotification()

        val result = provider.execute(notification.id)

        assertTrue(result.isSuccess)
        val updated = repository.findById(notification.id).get()
        assertNotNull(updated.cancelledAt)

        // Cleanup
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE id = ?", notification.id)
    }

    @Test
    fun `should fail when notification not found`() {
        val result = provider.execute(999999L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is NoSuchElementException)
    }

    @Test
    fun `should fail when notification already cancelled`() {
        val notification = createNotification(cancelledAt = LocalDateTime.now())

        val result = provider.execute(notification.id)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)

        // Cleanup
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE id = ?", notification.id)
    }
}
