package br.com.nomar.controlai.domain.purchases_invoices.usecase

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.NfceQrCode
import br.com.nomar.controlai.domain.purchases_invoices.exception.DuplicateInvoiceException
import br.com.nomar.controlai.domain.purchases_invoices.usecase.RegisterPendingInvoiceUseCase.Companion.METRIC_NAME
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RegisterPendingInvoiceUseCaseTest {

    private val requestContext = object : RequestContext {
        override val userId = 7L
        override val groupId = 42L
        override val email = "user@controlai.dev"
    }

    private val meterRegistry = SimpleMeterRegistry()
    private val clock = Clock.fixed(Instant.parse("2026-10-04T15:30:00Z"), ZoneOffset.UTC)

    private fun counter(result: String, uf: String) =
        meterRegistry.find(METRIC_NAME).tags("result", result, "uf", uf).counter()?.count()

    private fun savedModel(qrCode: NfceQrCode, groupId: Long, scannedAt: Instant) = PurchaseInvoiceModel(
        id = 1L, groupId = groupId, date = scannedAt, merchantName = null, merchantAddress = null, cnpj = null,
        totalItems = null, invoiceUrl = qrCode.invoiceUrl.value, accessKey = qrCode.accessKey.value,
        subtotal = null, total = null, taxes = null, discount = null, status = InvoiceStatus.PENDING,
    )

    @Test
    fun `should register the pending invoice in the request group with the scan time`() {
        var received: Triple<NfceQrCode, Long, Instant>? = null
        val useCase = RegisterPendingInvoiceUseCase(
            registerPendingInvoiceGateway = { qrCode, groupId, scannedAt ->
                received = Triple(qrCode, groupId, scannedAt)
                Result.success(savedModel(qrCode, groupId, scannedAt))
            },
            requestContext = requestContext,
            meterRegistry = meterRegistry,
            clock = clock,
        )

        val result = useCase.execute(VALID_QR)

        val invoice = result.getOrThrow()
        val (qrCode, groupId, scannedAt) = received!!
        assertEquals(VALID_QR, qrCode.invoiceUrl.value)
        assertEquals(RJ_KEY, qrCode.accessKey.value)
        assertEquals(42L, groupId)
        assertEquals(Instant.parse("2026-10-04T15:30:00Z"), scannedAt)
        assertEquals(InvoiceStatus.PENDING, invoice.status)
        assertEquals(1.0, counter("created", "33"))
    }

    @Test
    fun `should fail with the user-facing message and skip the gateway when the QR Code is invalid`() {
        var gatewayCalled = false
        val useCase = RegisterPendingInvoiceUseCase(
            registerPendingInvoiceGateway = { _, _, _ ->
                gatewayCalled = true
                Result.failure(IllegalStateException("should not be called"))
            },
            requestContext = requestContext,
            meterRegistry = meterRegistry,
            clock = clock,
        )

        val result = useCase.execute("00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-426655440000")

        val error = result.exceptionOrNull()
        assertIs<IllegalArgumentException>(error)
        assertEquals(NfceQrCode.NOT_NFCE_MESSAGE, error.message)
        assertTrue(!gatewayCalled)
        assertEquals(1.0, counter("invalid", "unknown"))
    }

    @Test
    fun `should propagate the duplicate failure from the gateway`() {
        val duplicate = DuplicateInvoiceException()
        val useCase = RegisterPendingInvoiceUseCase(
            registerPendingInvoiceGateway = { _, _, _ -> Result.failure(duplicate) },
            requestContext = requestContext,
            meterRegistry = meterRegistry,
            clock = clock,
        )

        val result = useCase.execute(VALID_QR)

        assertSame(duplicate, result.exceptionOrNull())
        assertEquals("Esta nota já foi registrada", duplicate.message)
        assertEquals(1.0, counter("duplicate", "33"))
        assertNull(counter("created", "33"))
    }

    @Test
    fun `should propagate unexpected gateway failures counting them as errors`() {
        val failure = RuntimeException("db down")
        val useCase = RegisterPendingInvoiceUseCase(
            registerPendingInvoiceGateway = { _, _, _ -> Result.failure(failure) },
            requestContext = requestContext,
            meterRegistry = meterRegistry,
            clock = clock,
        )

        val result = useCase.execute(VALID_QR)

        assertSame(failure, result.exceptionOrNull())
        assertEquals(1.0, counter("error", "33"))
        assertNull(counter("duplicate", "33"))
        assertNull(counter("created", "33"))
    }

    private companion object {
        const val RJ_KEY = "33260253358724000682650010000901721678115882"
        const val VALID_QR = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$RJ_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
    }
}
