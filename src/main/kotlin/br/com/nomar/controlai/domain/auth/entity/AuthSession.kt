package br.com.nomar.controlai.domain.auth.entity

class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: User,
)
