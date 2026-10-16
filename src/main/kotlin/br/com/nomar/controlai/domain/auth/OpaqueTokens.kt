package br.com.nomar.controlai.domain.auth

import java.security.SecureRandom
import java.util.Base64
import java.util.HexFormat

// Random values for one-off secrets: the login nonce (hex, as Apple expects) and the handoff code (URL-safe)
object OpaqueTokens {

    private val random = SecureRandom()

    fun hex(): String = HexFormat.of().formatHex(bytes())

    fun urlSafe(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes())

    private fun bytes(): ByteArray = ByteArray(BYTE_LENGTH).also(random::nextBytes)

    private const val BYTE_LENGTH = 32
}
