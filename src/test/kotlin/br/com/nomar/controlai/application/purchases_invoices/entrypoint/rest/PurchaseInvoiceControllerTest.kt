package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.gateway.AssociateInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.CancelPurchaseInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DeactivatePurchaseInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DisassociateInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchasesGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SearchNotificationsGateway
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.usecase.AssociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.CancelPurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DeactivatePurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DisassociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.SearchNotificationsUseCase
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import java.math.BigDecimal
import java.time.LocalDateTime

@Suppress("UNCHECKED_CAST")
private fun <T> stubOf(iface: Class<T>): T =
    Proxy.newProxyInstance(iface.classLoader, arrayOf(iface)) { _, _, _ ->
        throw UnsupportedOperationException("stub")
    } as T

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
        val deactivatePurchaseInvoiceUseCase = DeactivatePurchaseInvoiceUseCase(
            DeactivatePurchaseInvoiceGateway { Result.success(Unit) }
        )
        val cancelPurchaseInvoiceUseCase = CancelPurchaseInvoiceUseCase(
            CancelPurchaseInvoiceGateway { Result.success(Unit) }
        )
        val associateInvoiceUseCase = AssociateInvoiceUseCase(
            AssociateInvoiceGateway { _, _ -> Result.failure(UnsupportedOperationException("stub")) }
        )
        val disassociateInvoiceUseCase = DisassociateInvoiceUseCase(
            DisassociateInvoiceGateway { Result.success(Unit) }
        )
        val searchNotificationsUseCase = SearchNotificationsUseCase(
            SearchNotificationsGateway { _, _, _, _ -> Result.success(emptyList()) }
        )
        val controller = PurchaseInvoiceController(
            notifyPurchaseInvoiceQueueUseCase = notifyPurchaseInvoiceQueueUseCase,
            cancelPurchaseInvoiceUseCase = cancelPurchaseInvoiceUseCase,
            deactivatePurchaseInvoiceUseCase = deactivatePurchaseInvoiceUseCase,
            listPurchasesUseCase = listPurchasesUseCase,
            associateInvoiceUseCase = associateInvoiceUseCase,
            disassociateInvoiceUseCase = disassociateInvoiceUseCase,
            searchNotificationsUseCase = searchNotificationsUseCase,
            purchaseRepository = stubOf(PurchaseRepository::class.java),
            purchaseInvoiceRepository = stubOf(PurchaseInvoiceRepository::class.java),
            purchaseItemRepository = stubOf(PurchaseItemRepository::class.java),
            purchasePaymentRepository = stubOf(PurchasePaymentRepository::class.java),
            paymentNotificationRepository = stubOf(PaymentNotificationRepository::class.java),
            requestContext = object : RequestContext { override val userId = 1L; override val groupId = 1L },
        )

        val result = controller.listPurchases()

        assertEquals(2, result.size)
        assertEquals("Mercado A", result[0].merchantName)
        assertEquals("Mercado B", result[1].merchantName)
        assertEquals(2, result[0].totalItems)
        assertEquals(null, result[1].totalItems)
    }
}
