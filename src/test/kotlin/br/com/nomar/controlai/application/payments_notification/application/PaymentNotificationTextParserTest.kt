package br.com.nomar.controlai.application.payments_notification.application

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PaymentNotificationTextParserTest {

    private val parser = PaymentNotificationTextParser()

    @Test
    fun `should parse notification text with installments`() {
        val text = "FINAL 1234 EM 01/02/2026 10:30 NO VALOR DE R$ 1.234,56 EM 3X MERCADO CENTRAL."

        val result = parser.parse(text, "bank", "sms")

        assertEquals("1234", result.cardLastDigits)
        assertEquals("2026-02-01T10:30", result.purchasedAt.toString())
        assertEquals(0, result.amount.compareTo("1234.56".toBigDecimal()))
        assertEquals("MERCADO CENTRAL", result.merchantName)
        assertEquals(3, result.numberOfInstallments)
        assertEquals("bank", result.origin)
        assertEquals("SMS", result.originType)
    }

    @Test
    fun `should default installments to one and fallback origin type`() {
        val text = "FINAL 4321 EM 10/03/2026 08:15 NO VALOR DE R$ 89,90 PADARIA DO BAIRRO."

        val result = parser.parse(text, "app", "push")

        assertEquals(1, result.numberOfInstallments)
        assertEquals("HTTP_REQUEST", result.originType)
        assertEquals("PADARIA DO BAIRRO", result.merchantName)
    }

    @Test
    fun `should throw when notification text format is invalid`() {
        val invalidText = "texto sem o formato esperado"

        assertFailsWith<IllegalArgumentException> {
            parser.parse(invalidText, "app", "http_request")
        }
    }
}
