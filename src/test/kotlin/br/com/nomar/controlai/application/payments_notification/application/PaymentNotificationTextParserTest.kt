package br.com.nomar.controlai.application.payments_notification.application

import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
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
        assertEquals("2026-02-01T13:30:00Z", result.purchasedAt.toString())
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

        assertFailsWith<PaymentNotificationTextParseException> {
            parser.parse(invalidText, "app", "http_request")
        }
    }

    @Test
    fun `should parse bradesco cash payment format`() {
        val text = "BRADESCO CARTOES: COMPRA APROVADA NO CARTAO FINAL 9687 EM 17/03/2026 02:15. VALOR DE R$ 251,90 AMAZON  MARKETPLACE      SAO PAULO."

        val result = parser.parse(text, "bradesco", "sms")

        assertEquals("9687", result.cardLastDigits)
        assertEquals("2026-03-17T05:15:00Z", result.purchasedAt.toString())
        assertEquals(0, result.amount.compareTo("251.90".toBigDecimal()))
        assertEquals("AMAZON MARKETPLACE SAO PAULO", result.merchantName)
        assertEquals(1, result.numberOfInstallments)
    }

    @Test
    fun `should parse bradesco installment payment format with spaced x`() {
        val text = "BRADESCO CARTOES: COMPRA APROVADA NO CARTAO FINAL 9687 EM 14/03/2026 14:56 NO VALOR DE R$ 303,44 EM 2 X COBASI                   RIO DE JANEI."

        val result = parser.parse(text, "bradesco", "sms")

        assertEquals("9687", result.cardLastDigits)
        assertEquals("2026-03-14T17:56:00Z", result.purchasedAt.toString())
        assertEquals(0, result.amount.compareTo("303.44".toBigDecimal()))
        assertEquals("COBASI RIO DE JANEI", result.merchantName)
        assertEquals(2, result.numberOfInstallments)
    }

    @Test
    fun `should roll over to the next UTC day when SMS time is near midnight in Sao Paulo`() {
        val text = "FINAL 1234 EM 10/01/2026 23:30 NO VALOR DE R$ 50,00 LOJA NOTURNA."

        val result = parser.parse(text, "bank", "sms")

        assertEquals("2026-01-11T02:30:00Z", result.purchasedAt.toString())
    }

    @Test
    fun `should keep UTC day when Sao Paulo time is just after midnight`() {
        val text = "FINAL 1234 EM 11/01/2026 00:10 NO VALOR DE R$ 50,00 LOJA NOTURNA."

        val result = parser.parse(text, "bank", "sms")

        assertEquals("2026-01-11T03:10:00Z", result.purchasedAt.toString())
    }

    @Test
    fun `should parse itau notification format`() {
        val text = "Compra aprovada de R$ 43,12 em PETSUPERMARK*Or1023799785, 08/09 as 20:24 no seu Latam Itau final 8415."

        val result = parser.parse(text, "ITAU_CARTOES", "sms")

        assertEquals("8415", result.cardLastDigits)
        assertEquals(0, result.amount.compareTo("43.12".toBigDecimal()))
        assertEquals("PETSUPERMARK*Or1023799785", result.merchantName)
        assertEquals(1, result.numberOfInstallments)
        assertEquals("ITAU_CARTOES", result.origin)
        assertEquals("SMS", result.originType)
        assertEquals(expectedItauInstant("08/09", "20:24"), result.purchasedAt)
    }

    @Test
    fun `should throw when itau notification text format is invalid`() {
        val invalidText = "Compra aprovada em formato desconhecido"

        assertFailsWith<PaymentNotificationTextParseException> {
            parser.parse(invalidText, "ITAU_CARTOES", "sms")
        }
    }

    // Itau's SMS has no year, so the parser infers it from "now"; mirror that
    // inference here (using the real clock) instead of hardcoding a year.
    private fun expectedItauInstant(dayMonth: String, time: String): java.time.Instant {
        val saoPauloZone = ZoneId.of("America/Sao_Paulo")
        val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val now = ZonedDateTime.now(saoPauloZone)
        val withCurrentYear = LocalDateTime
            .parse("$dayMonth/${now.year} $time", formatter)
            .atZone(saoPauloZone)
        return if (withCurrentYear.isAfter(now.plusDays(1))) {
            withCurrentYear.minusYears(1).toInstant()
        } else {
            withCurrentYear.toInstant()
        }
    }
}
