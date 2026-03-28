package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchasesGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import java.math.BigDecimal
import java.time.LocalDateTime

class PurchaseInvoiceControllerTest {

    @Test
    fun `should list purchases`() {
        val purchases = listOf(
            Purchase(
                id = 1L,
                date = LocalDateTime.of(2026, 3, 1, 10, 30, 0),
                merchantName = "Mercado A",
                totalItems = 2,
                total = BigDecimal("100.00"),
            ),
            Purchase(
                id = 2L,
                date = LocalDateTime.of(2026, 2, 28, 9, 30, 0),
                merchantName = "Mercado B",
                totalItems = null,
                total = BigDecimal("50.00"),
            ),
        )

        val listPurchasesUseCase = ListPurchasesUseCase(
            ListPurchasesGateway { Result.success(purchases) }
        )
        val notifyPurchaseInvoiceQueueUseCase = NotifyPurchaseInvoiceQueueUseCase(
            NotifyPurchaseInvoiceQueueGateway { Result.success(Unit) }
        )
        val controller = PurchaseInvoiceController(
            notifyPurchaseInvoiceQueueUseCase = notifyPurchaseInvoiceQueueUseCase,
            listPurchasesUseCase = listPurchasesUseCase,
        )

        val result = controller.listPurchases()

        assertEquals(2, result.size)
        assertEquals("Mercado A", result[0].merchantName)
        assertEquals("Mercado B", result[1].merchantName)
        assertEquals(2, result[0].totalItems)
        assertEquals(null, result[1].totalItems)
    }
}
