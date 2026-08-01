package br.com.nomar.controlai.domain.auth.entity

class User(
    val id: Long? = null,
    val name: String,
    val email: String,
    val passwordHash: String? = null,
    val googleSub: String? = null,
)
