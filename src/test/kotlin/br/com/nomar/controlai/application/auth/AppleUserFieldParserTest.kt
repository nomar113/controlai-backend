package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.application.auth.application.AppleUserFieldParser
import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppleUserFieldParserTest {

    private val parser = AppleUserFieldParser(ObjectMapper())

    @Test
    fun `le nome completo e o e-mail do campo user`() {
        val result = parser.parse("""{"name":{"firstName":"John","lastName":"Doe"},"email":"john@example.com"}""")

        assertEquals("John Doe", result?.fullName)
        assertEquals("john@example.com", result?.email)
    }

    @Test
    fun `aceita nome sem sobrenome`() {
        val result = parser.parse("""{"name":{"firstName":"Maria"}}""")

        assertEquals("Maria", result?.fullName)
        assertNull(result?.email)
    }

    @Test
    fun `ignora espacos em branco ao redor dos nomes`() {
        val result = parser.parse("""{"name":{"firstName":"  Ana  ","lastName":"   "}}""")

        assertEquals("Ana", result?.fullName)
    }

    @Test
    fun `campo user ausente ou vazio retorna nulo`() {
        assertNull(parser.parse(null))
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
    }

    @Test
    fun `JSON malformado retorna nulo sem lancar excecao`() {
        assertNull(parser.parse("{nome: sem aspas"))
    }

    @Test
    fun `objeto sem nome nem e-mail retorna campos nulos`() {
        val result = parser.parse("{}")

        assertNull(result?.fullName)
        assertNull(result?.email)
    }
}
