package br.com.nomar.controlai.application.purchases_invoices.converter

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseInvoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.math.BigDecimal
import java.time.OffsetDateTime

class PurchaseInvoiceConverterTest {

    private val converter = PurchaseInvoiceConverter()

    @Test
    fun `should convert entity to model`() {
        val entity = sampleEntity()

        val model = converter.toModel(entity)

        assertEquals(entity.merchantName, model.merchantName)
        assertEquals(entity.cnpj.value, model.cnpj)
        assertEquals(entity.accessKey.value, model.accessKey)
        assertEquals(entity.totalItems.value, model.totalItems)
    }

    @Test
    fun `should convert model to entity`() {
        val model = sampleModel()

        val entity = converter.toEntity(model)

        assertEquals(model.id, entity.id)
        assertEquals("12.345.678/0001-90", entity.cnpj.value)
        assertEquals("12345678901234567890123456789012345678901234", entity.accessKey.value)
        assertEquals(0, entity.total.compareTo(BigDecimal("10.00")))
    }

    @Test
    fun `should throw when required model field is null`() {
        val model = sampleModel().copy(cnpj = null)

        val exception = assertFailsWith<IllegalStateException> {
            converter.toEntity(model)
        }

        assertEquals("cnpj não pode ser null", exception.message)
    }

    @Test
    fun `should convert items and payments to models`() {
        val item = PurchaseItem(
            productName = "Arroz",
            code = "123",
            quantity = BigDecimal("1.000"),
            unit = "UN",
            unitPrice = BigDecimal("10.00"),
            totalPrice = BigDecimal("10.00"),
        )
        val payment = PurchasePayment(
            type = "CREDIT_CARD",
            value = BigDecimal("10.00"),
        )

        val itemModels = converter.toItemModels(listOf(item), 99)
        val paymentModels = converter.toPaymentModels(listOf(payment), 99)

        assertEquals(1, itemModels.size)
        assertEquals(99, itemModels.first().purchaseInvoiceId)
        assertEquals(1, paymentModels.size)
        assertEquals(99, paymentModels.first().purchaseInvoiceId)
    }

    private fun sampleEntity() = PurchaseInvoice(
        id = 1,
        date = OffsetDateTime.parse("2026-02-01T10:30:00-03:00"),
        merchantName = "Mercado",
        merchantAddress = "Rua A",
        cnpj = Cnpj.of("12.345.678/0001-90"),
        totalItems = TotalItems.of(1),
        invoiceUrl = InvoiceUrl.of("https://example.com/invoice"),
        accessKey = AccessKey.of("12345678901234567890123456789012345678901234"),
        subtotal = BigDecimal("10.00"),
        total = BigDecimal("10.00"),
        taxes = BigDecimal("0.00"),
        discount = BigDecimal("0.00"),
    )

    private fun sampleModel() = PurchaseInvoiceModel(
        id = 1,
        date = OffsetDateTime.parse("2026-02-01T10:30:00-03:00"),
        merchantName = "Mercado",
        merchantAddress = "Rua A",
        cnpj = "12.345.678/0001-90",
        totalItems = 1,
        invoiceUrl = "https://example.com/invoice",
        accessKey = "12345678901234567890123456789012345678901234",
        subtotal = BigDecimal("10.00"),
        total = BigDecimal("10.00"),
        taxes = BigDecimal("0.00"),
        discount = BigDecimal("0.00"),
    )
}
