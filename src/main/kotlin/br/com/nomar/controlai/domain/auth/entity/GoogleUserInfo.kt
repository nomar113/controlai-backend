package br.com.nomar.controlai.domain.auth.entity

data class GoogleUserInfo(
    val sub: String,
    val email: String,
    val name: String,
)
