package br.com.nomar.controlai.domain.auth.entity

// What Apple posts to the callback. A cancel or an error arrives without the code and the identity token.
data class AppleCallback(
    val authorizationCode: String?,
    val identityToken: String?,
    val fullName: String?,
    val state: String?,
)
