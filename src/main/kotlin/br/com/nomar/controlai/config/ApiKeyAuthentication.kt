package br.com.nomar.controlai.config

import org.springframework.security.authentication.AbstractAuthenticationToken

// Populated by ApiKeyAuthFilter for requests authenticated via X-Api-Key header.
// There is no user principal — only the group owning the key is known.
class ApiKeyAuthentication(val groupId: Long) : AbstractAuthenticationToken(emptyList()) {
    init { isAuthenticated = true }

    override fun getCredentials(): Any? = null
    override fun getPrincipal(): String = "api-key-group-$groupId"
}
