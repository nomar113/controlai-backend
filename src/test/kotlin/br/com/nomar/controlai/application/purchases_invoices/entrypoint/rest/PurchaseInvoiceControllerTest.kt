package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchaseInvoicesGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchaseInvoicesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import java.math.BigDecimal
import java.time.OffsetDateTime

class PurchaseInvoiceControllerTest {

    @Test
    fun `should list purchases`() {
        val purchases = listOf(
            PurchaseInvoice(
                id = 1L,
                date = OffsetDateTime.parse("2026-03-01T10:30:00-03:00"),
                merchantName = "Mercado A",
                merchantAddress = "Rua A",
                cnpj = Cnpj.of("12.345.678/0001-90"),
                totalItems = TotalItems.of(2),
                invoiceUrl = InvoiceUrl.of("https://example.com/invoice-a"),
                accessKey = AccessKey.of("12345678901234567890123456789012345678901234"),
                subtotal = BigDecimal("100.00"),
                total = BigDecimal("100.00"),
                taxes = BigDecimal("10.00"),
                discount = BigDecimal("0.00"),
            ),
            PurchaseInvoice(
                id = 2L,
                date = OffsetDateTime.parse("2026-02-28T09:30:00-03:00"),
                merchantName = "Mercado B",
                merchantAddress = "Rua B",
                cnpj = Cnpj.of("98.765.432/0001-10"),
                totalItems = TotalItems.of(1),
                invoiceUrl = InvoiceUrl.of("https://example.com/invoice-b"),
                accessKey = AccessKey.of("09876543210987654321098765432109876543210987"),
                subtotal = BigDecimal("50.00"),
                total = BigDecimal("50.00"),
                taxes = BigDecimal("5.00"),
                discount = BigDecimal("0.00"),
            ),
        )

        val listPurchaseInvoicesUseCase = ListPurchaseInvoicesUseCase(
            ListPurchaseInvoicesGateway { Result.success(purchases) }
        )
        val notifyPurchaseInvoiceQueueUseCase = NotifyPurchaseInvoiceQueueUseCase(
            NotifyPurchaseInvoiceQueueGateway { Result.success(Unit) }
        )
        val controller = PurchaseInvoiceController(
            notifyPurchaseInvoiceQueueUseCase = notifyPurchaseInvoiceQueueUseCase,
            listPurchaseInvoicesUseCase = listPurchaseInvoicesUseCase,
        )

        val result = controller.listPurchases()

        assertEquals(2, result.size)
        assertEquals("Mercado A", result[0].merchantName)
        assertEquals("Mercado B", result[1].merchantName)
    }
}
