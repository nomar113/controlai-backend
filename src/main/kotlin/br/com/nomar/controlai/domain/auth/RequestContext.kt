package br.com.nomar.controlai.domain.auth

// Request-scoped view of the authenticated principal, populated from the JWT claims
interface RequestContext {
    val userId: Long
    val groupId: Long
}
