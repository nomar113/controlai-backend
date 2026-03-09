package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.OffsetDateTime

class PurchaseInvoiceControllerTest {

    @Test
    fun `should list purchases`() {
        val purchaseInvoiceRepository = mock(PurchaseInvoiceRepository::class.java)
        val notifyPurchaseInvoiceQueueUseCase = NotifyPurchaseInvoiceQueueUseCase(
            NotifyPurchaseInvoiceQueueGateway { Result.success(Unit) }
        )
        val controller = PurchaseInvoiceController(
            notifyPurchaseInvoiceQueueUseCase = notifyPurchaseInvoiceQueueUseCase,
            purchaseInvoiceRepository = purchaseInvoiceRepository,
        )

        `when`(purchaseInvoiceRepository.findAllByOrderByDateDesc()).thenReturn(
            listOf(
                PurchaseInvoiceModel(
                    id = 1L,
                    date = OffsetDateTime.parse("2026-03-01T10:30:00-03:00"),
                    merchantName = "Mercado A",
                    merchantAddress = "Rua A",
                    cnpj = "12.345.678/0001-90",
                    totalItems = 2,
                    invoiceUrl = "https://example.com/invoice-a",
                    accessKey = "12345678901234567890123456789012345678901234",
                    subtotal = BigDecimal("100.00"),
                    total = BigDecimal("100.00"),
                    taxes = BigDecimal("10.00"),
                    discount = BigDecimal("0.00"),
                ),
                PurchaseInvoiceModel(
                    id = 2L,
                    date = OffsetDateTime.parse("2026-02-28T09:30:00-03:00"),
                    merchantName = "Mercado B",
                    merchantAddress = "Rua B",
                    cnpj = "98.765.432/0001-10",
                    totalItems = 1,
                    invoiceUrl = "https://example.com/invoice-b",
                    accessKey = "09876543210987654321098765432109876543210987",
                    subtotal = BigDecimal("50.00"),
                    total = BigDecimal("50.00"),
                    taxes = BigDecimal("5.00"),
                    discount = BigDecimal("0.00"),
                ),
            )
        )

        val result = controller.listPurchases()

        assertEquals(2, result.size)
        assertEquals("Mercado A", result[0].merchantName)
        assertEquals("Mercado B", result[1].merchantName)
    }
}
