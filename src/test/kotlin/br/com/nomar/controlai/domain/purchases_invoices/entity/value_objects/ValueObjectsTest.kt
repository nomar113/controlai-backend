package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValueObjectsTest {

    @Test
    fun `should create access key with valid value`() {
        val accessKey = AccessKey.of("12345678901234567890123456789012345678901234")

        assertEquals("12345678901234567890123456789012345678901234", accessKey.value)
    }

    @Test
    fun `should fail when access key does not have 44 digits`() {
        assertFailsWith<IllegalArgumentException> {
            AccessKey.of("123")
        }
    }

    @Test
    fun `should extract access key from invoice url with pipe-separated p param`() {
        val invoiceUrl = InvoiceUrl.of(
            "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=33260253358724000682650010000901721678115882|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d"
        )

        val accessKey = AccessKey.fromInvoiceUrl(invoiceUrl)

        assertEquals("33260253358724000682650010000901721678115882", accessKey.value)
    }

    @Test
    fun `should fail to extract access key when invoice url has no query string`() {
        assertFailsWith<IllegalArgumentException> {
            AccessKey.fromInvoiceUrl(InvoiceUrl.of("https://example.com/invoice/123"))
        }
    }

    @Test
    fun `should fail to extract access key when invoice url has no p param`() {
        assertFailsWith<IllegalArgumentException> {
            AccessKey.fromInvoiceUrl(InvoiceUrl.of("https://example.com/invoice?other=1"))
        }
    }

    @Test
    fun `should create cnpj with valid value`() {
        val cnpj = Cnpj.of("12.345.678/0001-90")

        assertEquals("12.345.678/0001-90", cnpj.value)
    }

    @Test
    fun `should fail when cnpj format is invalid`() {
        assertFailsWith<IllegalArgumentException> {
            Cnpj.of("12345678000190")
        }
    }

    @Test
    fun `should create invoice url with valid value`() {
        val invoiceUrl = InvoiceUrl.of("https://example.com/invoice/123")

        assertEquals("https://example.com/invoice/123", invoiceUrl.asString())
    }

    @Test
    fun `should create invoice url with pipe in query`() {
        val invoiceUrl = InvoiceUrl.of("http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=33260253358724000682650010000901721678115882|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d")

        assertEquals(
            "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=33260253358724000682650010000901721678115882|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d",
            invoiceUrl.asString(),
        )
    }

    @Test
    fun `should fail when invoice url is blank`() {
        assertFailsWith<IllegalArgumentException> {
            InvoiceUrl.of("   ")
        }
    }

    @Test
    fun `should create total items with positive value`() {
        val totalItems = TotalItems.of(3)

        assertEquals(3, totalItems.asInt())
    }

    @Test
    fun `should fail when total items is zero`() {
        assertFailsWith<IllegalArgumentException> {
            TotalItems.of(0)
        }
    }
}
