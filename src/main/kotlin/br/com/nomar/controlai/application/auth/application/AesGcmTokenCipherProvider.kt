package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.gateway.TokenCipherGateway
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// Output is base64(iv || ciphertext || tag). A fresh random IV is used for every call.
@Component
class AesGcmTokenCipherProvider(
    @Value("\${apple.token-encryption-key}") base64Key: String,
) : TokenCipherGateway {

    private val key: SecretKey = Base64.getDecoder().decode(base64Key).let { bytes ->
        require(bytes.size == KEY_BYTES) { "APPLE_TOKEN_ENCRYPTION_KEY deve ter 32 bytes em base64" }
        SecretKeySpec(bytes, "AES")
    }

    private val random = SecureRandom()

    override fun encrypt(plainText: String): String {
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + encrypted)
    }

    // Throws AEADBadTagException when the stored value was tampered with
    override fun decrypt(cipherText: String): String {
        val bytes = Base64.getDecoder().decode(cipherText)
        require(bytes.size > IV_BYTES) { "Dado cifrado invalido" }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, bytes.copyOfRange(0, IV_BYTES)))
        }
        return String(cipher.doFinal(bytes.copyOfRange(IV_BYTES, bytes.size)), Charsets.UTF_8)
    }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BYTES = 32
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
