package br.com.nomar.controlai.domain.auth.gateway

interface PasswordHashGateway {
    fun hash(rawPassword: String): String
    fun matches(rawPassword: String, passwordHash: String): Boolean
}
