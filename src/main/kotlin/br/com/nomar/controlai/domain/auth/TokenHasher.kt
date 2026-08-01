package br.com.nomar.controlai.domain.auth

import java.security.MessageDigest

// Opaque refresh tokens are never stored in plain text, only their SHA-256 hex digest
object TokenHasher {

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
