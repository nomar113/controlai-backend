package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class NfceQrCodeTest {

    private companion object {
        // Keys generated with a valid modulo 11 check digit (RJ_KEY is a real anonymized key)
        const val RJ_KEY = "33260253358724000682650010000901721678115882"
        const val RJ_OFFLINE_KEY = "33260512345678000190650010000123459876543219"
        const val SP_KEY = "35260512345678000190650010000123451123456784"
        const val MG_KEY = "31260512345678000190650010000123451123456785"
        const val MODEL_55_KEY = "33260512345678000190550010000123451123456787"
        const val UNKNOWN_UF_KEY = "99260512345678000190650010000123451123456781"
        const val INVALID_DV_KEY = "33260253358724000682650010000901721678115883"

        const val RJ_URL = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode"
        const val SP_URL = "https://www.nfce.fazenda.sp.gov.br/NFCeConsultaPublica/Paginas/ConsultaQRCode.aspx"
        const val MG_URL = "https://portalsped.fazenda.mg.gov.br/portalnfce/sistema/qrcode.xhtml"
    }

    private fun assertRejected(rawContent: String, expectedMessage: String) {
        val error = assertFailsWith<IllegalArgumentException> { NfceQrCode.parse(rawContent) }
        assertEquals(expectedMessage, error.message)
    }

    @Test
    fun `should accept v2 online QR Code`() {
        val qrCode = NfceQrCode.parse("$RJ_URL?p=$RJ_KEY|2|1|1|c77e3a5c4f7a9ad7d25fee080cac222faac1219d")

        assertEquals(RJ_KEY, qrCode.accessKey.value)
        assertEquals("33", qrCode.uf)
    }

    @Test
    fun `should accept v2 offline QR Code`() {
        val qrCode = NfceQrCode.parse(
            "$RJ_URL?p=$RJ_OFFLINE_KEY|2|1|05|25.90|4f6a72324b4e78614d7a466c4c7a5a6d|1|9c1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f90",
        )

        assertEquals(RJ_OFFLINE_KEY, qrCode.accessKey.value)
    }

    @Test
    fun `should accept v3 online QR Code`() {
        val qrCode = NfceQrCode.parse("$SP_URL?p=$SP_KEY|3|1")

        assertEquals(SP_KEY, qrCode.accessKey.value)
        assertEquals("35", qrCode.uf)
    }

    @Test
    fun `should accept v3 offline QR Code with long signature`() {
        val signature = "A".repeat(344)
        val rawContent = "$RJ_URL?p=$RJ_OFFLINE_KEY|3|1|05|25.90|2|12345678000190|$signature"

        val qrCode = NfceQrCode.parse(rawContent)

        assertEquals(RJ_OFFLINE_KEY, qrCode.accessKey.value)
        assertEquals(rawContent, qrCode.invoiceUrl.asString())
        assertTrue(rawContent.length > 255)
    }

    @Test
    fun `should accept p parameter with encoded pipe separator`() {
        val qrCode = NfceQrCode.parse("$RJ_URL?p=$RJ_KEY%7C2%7C1%7C1%7Cc77e3a5c4f7a9ad7d25fee080cac222faac1219d")

        assertEquals(RJ_KEY, qrCode.accessKey.value)
    }

    @Test
    fun `should accept p parameter with lowercase encoded pipe separator`() {
        val qrCode = NfceQrCode.parse("$RJ_URL?p=$RJ_KEY%7c2%7c1")

        assertEquals(RJ_KEY, qrCode.accessKey.value)
    }

    @Test
    fun `should accept http url`() {
        val rawContent = "$RJ_URL?p=$RJ_KEY|2|1|1|abc"

        val qrCode = NfceQrCode.parse(rawContent)

        assertEquals(rawContent, qrCode.invoiceUrl.asString())
    }

    @Test
    fun `should accept legacy v1 chNFe parameter`() {
        val qrCode = NfceQrCode.parse("$MG_URL?chNFe=$MG_KEY&nVersao=100&tpAmb=1&cDest=&dhEmi=323032362d30352d3130&vNF=25.90&cHashQRCode=abc")

        assertEquals(MG_KEY, qrCode.accessKey.value)
        assertEquals("31", qrCode.uf)
    }

    @Test
    fun `should prefer p over chNFe when both are present`() {
        val qrCode = NfceQrCode.parse("$RJ_URL?chNFe=$MG_KEY&p=$RJ_KEY|2|1")

        assertEquals(RJ_KEY, qrCode.accessKey.value)
    }

    @Test
    fun `should accept uppercase scheme`() {
        val qrCode = NfceQrCode.parse("HTTPS://www.nfce.fazenda.sp.gov.br/qrcode?p=$SP_KEY|2|1")

        assertEquals(SP_KEY, qrCode.accessKey.value)
    }

    @Test
    fun `should accept keys from RJ SP and MG`() {
        listOf(
            "$RJ_URL?p=$RJ_KEY|2|1" to "33",
            "$SP_URL?p=$SP_KEY|2|1" to "35",
            "$MG_URL?p=$MG_KEY|2|1" to "31",
        ).forEach { (rawContent, uf) ->
            assertEquals(uf, NfceQrCode.parse(rawContent).uf)
        }
    }

    @Test
    fun `should keep invoice url identical to the raw content`() {
        val rawContent = "http://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$RJ_KEY%7C2%7C1%7C1%7CABC&extra= x#frag"

        val qrCode = NfceQrCode.parse(rawContent)

        assertEquals(rawContent, qrCode.invoiceUrl.asString())
    }

    @Test
    fun `should reject model 55 access key`() {
        assertRejected("$RJ_URL?p=$MODEL_55_KEY|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject invalid check digit`() {
        assertRejected("$RJ_URL?p=$INVALID_DV_KEY|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject unknown uf code`() {
        assertRejected("$RJ_URL?p=$UNKNOWN_UF_KEY|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject access key with 43 digits`() {
        assertRejected("$RJ_URL?p=${RJ_KEY.dropLast(1)}|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject access key with 45 digits`() {
        assertRejected("$RJ_URL?p=${RJ_KEY}0|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject pix payload`() {
        assertRejected(
            "00020126580014br.gov.bcb.pix0136123e4567-e12b-12d1-a456-4266554400005204000053039865802BR5913Fulano de Tal6008BRASILIA62070503***63041D3D",
            NfceQrCode.NOT_NFCE_MESSAGE,
        )
    }

    @Test
    fun `should reject text without url`() {
        assertRejected("qualquer texto sem link", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject non http scheme`() {
        assertRejected("ftp://www4.fazenda.rj.gov.br/consultaNFCe/QRCode?p=$RJ_KEY|2|1", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject url without p or chNFe`() {
        assertRejected("https://www.example.com/page?q=$RJ_KEY", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject site url with short p parameter`() {
        assertRejected("https://site.com.br/?p=123", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject url without query`() {
        assertRejected("https://www.example.com/page", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should reject empty string`() {
        assertRejected("", NfceQrCode.NOT_NFCE_MESSAGE)
    }

    @Test
    fun `should expose the QR Code version`() {
        assertEquals(2, NfceQrCode.parse("$RJ_URL?p=$RJ_KEY|2|1|1|c77e3a5c").version)
        assertEquals(3, NfceQrCode.parse("$SP_URL?p=$SP_KEY|3|1").version)
        assertEquals(1, NfceQrCode.parse("$MG_URL?chNFe=$MG_KEY&nVersao=100").version)
        assertEquals(null, NfceQrCode.parse("$RJ_URL?p=$RJ_KEY").version)
    }
}
