package br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseItemRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchasePaymentRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.rest.request.ExtractPurchaseInvoiceRequest
import br.com.nomar.controlai.domain.purchases_invoices.entity.Purchase
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionBlockedException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionNavigationException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionTimeoutException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.AssociateInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.CancelPurchaseInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DeactivatePurchaseInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.DisassociateInvoiceGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.ListPurchasesGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NfceExtractionGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NotifyPurchaseInvoiceQueueGateway
import br.com.nomar.controlai.domain.purchases_invoices.gateway.SearchNotificationsGateway
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.purchases_invoices.usecase.AssociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.CancelPurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DeactivatePurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.DisassociateInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ExtractPurchaseInvoiceUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.ListPurchasesUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.NotifyPurchaseInvoiceQueueUseCase
import br.com.nomar.controlai.domain.purchases_invoices.usecase.SearchNotificationsUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
            extractPurchaseInvoiceUseCase = ExtractPurchaseInvoiceUseCase(
                NfceExtractionGateway { Result.failure(UnsupportedOperationException("stub")) },
                stubOf(PurchaseInvoiceRepository::class.java),
            ),
            purchaseRepository = stubOf(PurchaseRepository::class.java),
            purchaseInvoiceRepository = stubOf(PurchaseInvoiceRepository::class.java),
            purchaseItemRepository = stubOf(PurchaseItemRepository::class.java),
            purchasePaymentRepository = stubOf(PurchasePaymentRepository::class.java),
            paymentNotificationRepository = stubOf(PaymentNotificationRepository::class.java),
            requestContext = object : RequestContext { override val userId = 1L; override val groupId = 1L; override val email = "test@example.com" },
        )

        val result = controller.listPurchases()

        assertEquals(2, result.size)
        assertEquals("Mercado A", result[0].merchantName)
        assertEquals("Mercado B", result[1].merchantName)
        assertEquals(2, result[0].totalItems)
        assertEquals(null, result[1].totalItems)
    }

    private fun buildControllerForExtraction(extractPurchaseInvoiceUseCase: ExtractPurchaseInvoiceUseCase) =
        PurchaseInvoiceController(
            notifyPurchaseInvoiceQueueUseCase = NotifyPurchaseInvoiceQueueUseCase(
                NotifyPurchaseInvoiceQueueGateway { Result.failure(UnsupportedOperationException("stub")) }
            ),
            cancelPurchaseInvoiceUseCase = CancelPurchaseInvoiceUseCase(
                CancelPurchaseInvoiceGateway { Result.failure(UnsupportedOperationException("stub")) }
            ),
            deactivatePurchaseInvoiceUseCase = DeactivatePurchaseInvoiceUseCase(
                DeactivatePurchaseInvoiceGateway { Result.failure(UnsupportedOperationException("stub")) }
            ),
            listPurchasesUseCase = ListPurchasesUseCase(
                ListPurchasesGateway { Result.failure(UnsupportedOperationException("stub")) }
            ),
            associateInvoiceUseCase = AssociateInvoiceUseCase(
                AssociateInvoiceGateway { _, _ -> Result.failure(UnsupportedOperationException("stub")) }
            ),
            disassociateInvoiceUseCase = DisassociateInvoiceUseCase(
                DisassociateInvoiceGateway { Result.failure(UnsupportedOperationException("stub")) }
            ),
            searchNotificationsUseCase = SearchNotificationsUseCase(
                SearchNotificationsGateway { _, _, _, _ -> Result.failure(UnsupportedOperationException("stub")) }
            ),
            extractPurchaseInvoiceUseCase = extractPurchaseInvoiceUseCase,
            purchaseRepository = stubOf(PurchaseRepository::class.java),
            purchaseInvoiceRepository = stubOf(PurchaseInvoiceRepository::class.java),
            purchaseItemRepository = stubOf(PurchaseItemRepository::class.java),
            purchasePaymentRepository = stubOf(PurchasePaymentRepository::class.java),
            paymentNotificationRepository = stubOf(PaymentNotificationRepository::class.java),
            requestContext = object : RequestContext { override val userId = 1L; override val groupId = 1L; override val email = "test@example.com" },
        )

    private val accessKey = "12345678901234567890123456789012345678901234"
    private val invoiceUrl = "https://www.example.com/nfce?p=$accessKey|2|1|1|abc123"

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

    private fun useCaseWithGateway(
        repositoryCount: Long = 0,
        gatewayResult: Result<ExtractedPurchaseInvoice>,
    ): ExtractPurchaseInvoiceUseCase {
        val repository = stubOf(PurchaseInvoiceRepository::class.java)
        return ExtractPurchaseInvoiceUseCase(
            NfceExtractionGateway { gatewayResult },
            object : PurchaseInvoiceRepository by repository {
                override fun countByAccessKey(accessKey: String) = repositoryCount
            },
        )
    }

    @Test
    fun `POST invoice extraction should return 200 with extracted data on success`() {
        val useCase = useCaseWithGateway(gatewayResult = Result.success(extractedInvoice))
        val controller = buildControllerForExtraction(useCase)

        val response = controller.extractPurchaseInvoice(ExtractPurchaseInvoiceRequest(invoiceUrl))

        assertEquals("Mercado Teste", response.merchantName)
        assertEquals(BigDecimal("10.00"), response.total)
    }

    @Test
    fun `POST invoice extraction should return 409 when accessKey already registered`() {
        val useCase = useCaseWithGateway(
            repositoryCount = 1,
            gatewayResult = Result.success(extractedInvoice),
        )
        val controller = buildControllerForExtraction(useCase)

        val exception = assertFailsWith<ResponseStatusException> {
            controller.extractPurchaseInvoice(ExtractPurchaseInvoiceRequest(invoiceUrl))
        }

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
        assertEquals("Nota já registrada", exception.reason)
    }

    @Test
    fun `POST invoice extraction should return 422 when SEFAZ blocks the query`() {
        val useCase = useCaseWithGateway(
            gatewayResult = Result.failure(NfceExtractionBlockedException("Nota bloqueada pela SEFAZ")),
        )
        val controller = buildControllerForExtraction(useCase)

        val exception = assertFailsWith<ResponseStatusException> {
            controller.extractPurchaseInvoice(ExtractPurchaseInvoiceRequest(invoiceUrl))
        }

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, exception.statusCode)
        assertEquals("Nota bloqueada pela SEFAZ", exception.reason)
    }

    @Test
    fun `POST invoice extraction should return 504 on timeout`() {
        val useCase = useCaseWithGateway(gatewayResult = Result.failure(NfceExtractionTimeoutException()))
        val controller = buildControllerForExtraction(useCase)

        val exception = assertFailsWith<ResponseStatusException> {
            controller.extractPurchaseInvoice(ExtractPurchaseInvoiceRequest(invoiceUrl))
        }

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, exception.statusCode)
        assertEquals("Tempo esgotado ao consultar a SEFAZ", exception.reason)
    }

    @Test
    fun `POST invoice extraction should return 502 on navigation or service error`() {
        val useCase = useCaseWithGateway(gatewayResult = Result.failure(NfceExtractionNavigationException()))
        val controller = buildControllerForExtraction(useCase)

        val exception = assertFailsWith<ResponseStatusException> {
            controller.extractPurchaseInvoice(ExtractPurchaseInvoiceRequest(invoiceUrl))
        }

        assertEquals(HttpStatus.BAD_GATEWAY, exception.statusCode)
        assertEquals("Não foi possível consultar a SEFAZ", exception.reason)
    }
}
