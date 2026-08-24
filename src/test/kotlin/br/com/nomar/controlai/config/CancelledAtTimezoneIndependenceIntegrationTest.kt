package br.com.nomar.controlai.config

import br.com.nomar.controlai.application.payments_notification.application.CancelPaymentNotificationProvider
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.application.CancelPurchaseInvoiceProvider
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Tarefa 4.0: PaymentNotificationResponse/PurchaseInvoiceDetailResponse convertem `cancelledAt`
 * (ainda `LocalDateTime` — task 2.0 nao cobriu esse campo) para `Instant` assumindo que seu
 * valor wall-clock ja e UTC. Essa suposicao so e valida porque CancelPaymentNotificationProvider
 * / CancelPurchaseInvoiceProvider gravam `LocalDateTime.now(ZoneOffset.UTC)` explicitamente — nao
 * `LocalDateTime.now()`, que dependeria do timezone default (nao fixado) da JVM. Este teste prova
 * que a conversao produz o instante real, independentemente do timezone default da JVM.
 */
@SpringBootTest
class CancelledAtTimezoneIndependenceIntegrationTest {

    @Autowired
    private lateinit var cancelPaymentNotificationProvider: CancelPaymentNotificationProvider

    @Autowired
    private lateinit var paymentNotificationRepository: PaymentNotificationRepository

    @Autowired
    private lateinit var cancelPurchaseInvoiceProvider: CancelPurchaseInvoiceProvider

    @Autowired
    private lateinit var purchaseInvoiceRepository: PurchaseInvoiceRepository

    private val createdNotificationIds = mutableListOf<Long>()
    private val createdInvoiceIds = mutableListOf<Long>()
    private val originalDefaultTimeZone: TimeZone = TimeZone.getDefault()

    @BeforeEach
    fun authenticate() = TestSecurityContext.authenticateAsGroup()

    @AfterEach
    fun cleanup() {
        TimeZone.setDefault(originalDefaultTimeZone)
        createdNotificationIds.forEach { paymentNotificationRepository.deleteById(it) }
        createdInvoiceIds.forEach { purchaseInvoiceRepository.deleteById(it) }
        createdNotificationIds.clear()
        createdInvoiceIds.clear()
        TestSecurityContext.clear()
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "America/Sao_Paulo"])
    fun `PaymentNotification cancelledAt converts to the real UTC instant regardless of the JVM default timezone`(zoneId: String) {
        val notification = paymentNotificationRepository.save(
            PaymentNotification(
                purchasedAt = Instant.now(),
                amount = BigDecimal("10.00"),
                merchantName = "Cancel Timezone Probe",
                origin = "MANUAL",
                originType = "MANUAL",
            )
        )
        createdNotificationIds.add(notification.id)

        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        val before = Instant.now()
        cancelPaymentNotificationProvider.execute(notification.id).getOrThrow()
        val after = Instant.now()

        val reloaded = paymentNotificationRepository.findById(notification.id).get()
        val cancelledAtAsInstant = reloaded.cancelledAt!!.atZone(ZoneOffset.UTC).toInstant()

        // The TIMESTAMP column truncates to whole seconds, so allow a small tolerance around the
        // [before, after] window instead of nanosecond-exact bounds.
        assertFalse(cancelledAtAsInstant.isBefore(before.minusSeconds(2)))
        assertFalse(cancelledAtAsInstant.isAfter(after.plusSeconds(2)))
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "America/Sao_Paulo"])
    fun `PurchaseInvoice cancelledAt converts to the real UTC instant regardless of the JVM default timezone`(zoneId: String) {
        val invoice = purchaseInvoiceRepository.save(
            PurchaseInvoiceModel(
                groupId = 1L,
                date = Instant.now(),
                merchantName = "Cancel Timezone Probe",
                merchantAddress = null,
                cnpj = null,
                totalItems = null,
                invoiceUrl = null,
                accessKey = null,
                subtotal = BigDecimal("10.00"),
                total = BigDecimal("10.00"),
                taxes = BigDecimal.ZERO,
                discount = BigDecimal.ZERO,
            )
        )
        createdInvoiceIds.add(invoice.id!!)

        TimeZone.setDefault(TimeZone.getTimeZone(zoneId))
        val before = Instant.now()
        cancelPurchaseInvoiceProvider.execute(invoice.id!!).getOrThrow()
        val after = Instant.now()

        val reloaded = purchaseInvoiceRepository.findById(invoice.id!!).get()
        val cancelledAtAsInstant = reloaded.cancelledAt!!.atZone(ZoneOffset.UTC).toInstant()

        // The TIMESTAMP column truncates to whole seconds, so allow a small tolerance around the
        // [before, after] window instead of nanosecond-exact bounds.
        assertTrue(
            !cancelledAtAsInstant.isBefore(before.minusSeconds(2)) &&
                !cancelledAtAsInstant.isAfter(after.plusSeconds(2))
        )
    }
}
