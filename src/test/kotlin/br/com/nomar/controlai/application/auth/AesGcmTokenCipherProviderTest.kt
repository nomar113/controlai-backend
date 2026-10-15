package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.application.auth.application.AesGcmTokenCipherProvider
import java.util.Base64
import javax.crypto.AEADBadTagException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AesGcmTokenCipherProviderTest {

    private val key = Base64.getEncoder().encodeToString(ByteArray(32) { it.toByte() })
    private val cipher = AesGcmTokenCipherProvider(key)

    @Test
    fun `cifragem e decifragem devolvem o texto original`() {
        val refreshToken = "r.1234567890abcdefghijklmnopqrstuvwxyz"

        val encrypted = cipher.encrypt(refreshToken)

        assertEquals(refreshToken, cipher.decrypt(encrypted))
    }

    @Test
    fun `o valor cifrado nao contem o token em texto puro`() {
        val refreshToken = "r.super-secret-refresh-token"

        val encrypted = cipher.encrypt(refreshToken)

        assertFalse(encrypted.contains(refreshToken))
        assertFalse(encrypted.contains("super-secret"))
    }

    @Test
    fun `cada cifragem usa um IV novo e gera valor diferente`() {
        val first = cipher.encrypt("same-token")
        val second = cipher.encrypt("same-token")

        assertNotEquals(first, second)
        assertEquals("same-token", cipher.decrypt(first))
        assertEquals("same-token", cipher.decrypt(second))
    }

    @Test
    fun `dado adulterado e rejeitado`() {
        val encrypted = cipher.encrypt("refresh-token")
        val bytes = Base64.getDecoder().decode(encrypted)
        // Flip one bit in the ciphertext region (after the 12-byte IV)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().encodeToString(bytes)

        assertFailsWith<AEADBadTagException> { cipher.decrypt(tampered) }
    }

    @Test
    fun `chave com tamanho diferente de 32 bytes e rejeitada na inicializacao`() {
        val shortKey = Base64.getEncoder().encodeToString(ByteArray(16))

        assertTrue(runCatching { AesGcmTokenCipherProvider(shortKey) }.isFailure)
    }
}
