package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.time.Instant
import java.util.TimeZone

/**
 * Complementa TimezoneJdbcIntegrationTest (Tarefa 1.0, tabela de sondagem generica) validando,
 * agora com uma entidade JPA real ja padronizada na Tarefa 2.0 (PaymentNotification.purchasedAt),
 * que um Instant gravado e lido de volta e identico independentemente do timezone default da JVM.
 */
@SpringBootTest
class EntityTimezoneIndependenceIntegrationTest {

    @Autowired
    private lateinit var paymentNotificationRepository: PaymentNotificationRepository

    @AfterEach
    fun tearDown() {
        paymentNotificationRepository.deleteAll(
            paymentNotificationRepository.findAll().filter { it.merchantName == "Timezone Probe" }
        )
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "America/Sao_Paulo"])
    fun `a purchasedAt instant round-trips identically regardless of the JVM default timezone`(zoneId: String) {
        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))

            val known: Instant = Instant.parse("2026-01-15T23:30:00Z")
            val saved = paymentNotificationRepository.save(
                PaymentNotification(
                    purchasedAt = known,
                    amount = BigDecimal("10.00"),
                    merchantName = "Timezone Probe",
                    origin = "MANUAL",
                    originType = "MANUAL",
                )
            )

            val reloaded = paymentNotificationRepository.findByIdAndGroupId(saved.id, saved.groupId)

            assertEquals(known, reloaded?.purchasedAt)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
