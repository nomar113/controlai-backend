package br.com.nomar.controlai.domain.auth.entity

// The e-mail is not part of the command on purpose: the request body is not signed, so only the e-mail
// inside the verified identityToken is trusted to link or create accounts
data class AppleLoginCommand(
    val identityToken: String,
    val authorizationCode: String,
    val nonce: String,
    val fullName: String?,
)
