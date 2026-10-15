package br.com.nomar.controlai.domain.auth

import br.com.nomar.controlai.domain.auth.entity.AppleRefreshCredential
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AppleRefreshCredentialTest {

    @Test
    fun `serializa e lê de volta o client_id e o refresh_token`() {
        val credential = AppleRefreshCredential("br.com.nomar.controlai", "rt-123")

        assertEquals(credential, AppleRefreshCredential.parse(credential.serialize()))
    }

    @Test
    fun `valor sem separador ou com parte vazia e rejeitado`() {
        assertFailsWith<IllegalArgumentException> { AppleRefreshCredential.parse("so-o-token") }
        assertFailsWith<IllegalArgumentException> { AppleRefreshCredential.parse("\nrt-123") }
        assertFailsWith<IllegalArgumentException> { AppleRefreshCredential.parse("br.com.nomar.controlai\n") }
    }
}
