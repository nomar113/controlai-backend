package br.com.nomar.controlai.domain.purchases_invoices

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionBlockedException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionNavigationException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionTimeoutException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NfceExtractionGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ExtractPurchaseInvoiceUseCase
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ExtractPurchaseInvoiceUseCaseTest {

    private val accessKey = "12345678901234567890123456789012345678901234"
    private val invoiceUrl = InvoiceUrl.of("https://www.example.com/nfce?p=$accessKey|2|1|1|abc123")

    private val extractedInvoice = ExtractedPurchaseInvoice(
        merchantName = "Mercado Teste",
        cnpj = "12345678000199",
        merchantAddress = "Rua A, 123",
        totalItems = 1,
        subtotal = BigDecimal("10.00"),
        discount = BigDecimal.ZERO,
        total = BigDecimal("10.00"),
        taxes = BigDecimal("1.50"),
        date = "28/08/2026 10:00:00-03:00",
        items = emptyList(),
        payments = emptyList(),
    )

    private fun repositoryWithCount(count: Long): PurchaseInvoiceRepository {
        val repository = mock<PurchaseInvoiceRepository>()
        `when`(repository.countByAccessKey(accessKey)).thenReturn(count)
        return repository
    }

    @Test
    fun `should return extracted invoice when accessKey is new`() {
        val repository = repositoryWithCount(0)
        val gateway = NfceExtractionGateway { Result.success(extractedInvoice) }
        val useCase = ExtractPurchaseInvoiceUseCase(gateway, repository)

        val result = useCase.execute(invoiceUrl)

        assertTrue(result.isSuccess)
        assertEquals(extractedInvoice, result.getOrThrow())
    }

    @Test
    fun `should fail with duplicate message and not call gateway when accessKey already registered`() {
        val repository = repositoryWithCount(1)
        var gatewayCalled = false
        val gateway = NfceExtractionGateway {
            gatewayCalled = true
            Result.success(extractedInvoice)
        }
        val useCase = ExtractPurchaseInvoiceUseCase(gateway, repository)

        val result = useCase.execute(invoiceUrl)

        assertTrue(result.isFailure)
        assertEquals("Nota já registrada", result.exceptionOrNull()?.message)
        assertTrue(!gatewayCalled)
    }

    @Test
    fun `should propagate blocked exception from gateway`() {
        val repository = repositoryWithCount(0)
        val gateway = NfceExtractionGateway { Result.failure(NfceExtractionBlockedException("SEFAZ bloqueou")) }
        val useCase = ExtractPurchaseInvoiceUseCase(gateway, repository)

        val result = useCase.execute(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionBlockedException>(result.exceptionOrNull())
    }

    @Test
    fun `should propagate timeout exception from gateway`() {
        val repository = repositoryWithCount(0)
        val gateway = NfceExtractionGateway { Result.failure(NfceExtractionTimeoutException()) }
        val useCase = ExtractPurchaseInvoiceUseCase(gateway, repository)

        val result = useCase.execute(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionTimeoutException>(result.exceptionOrNull())
    }

    @Test
    fun `should propagate navigation exception from gateway`() {
        val repository = repositoryWithCount(0)
        val gateway = NfceExtractionGateway { Result.failure(NfceExtractionNavigationException()) }
        val useCase = ExtractPurchaseInvoiceUseCase(gateway, repository)

        val result = useCase.execute(invoiceUrl)

        assertTrue(result.isFailure)
        assertIs<NfceExtractionNavigationException>(result.exceptionOrNull())
    }
}
