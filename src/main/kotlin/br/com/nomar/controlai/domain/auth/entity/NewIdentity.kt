package br.com.nomar.controlai.domain.auth.entity

// External identity to store together with a brand-new user
data class NewIdentity(
    val provider: IdentityProvider,
    val subject: String,
    val appleRefreshTokenEnc: String? = null,
)
