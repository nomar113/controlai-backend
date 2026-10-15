package br.com.nomar.controlai.domain.auth.entity

data class AppleIdentity(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val isPrivateRelay: Boolean,
    // Client ID the token was issued for (bundle ID or Services ID); required as client_id in the code exchange
    val audience: String,
)
