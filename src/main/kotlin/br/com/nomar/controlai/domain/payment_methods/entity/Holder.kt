package br.com.nomar.controlai.domain.payment_methods.entity

import java.time.LocalDateTime

class Holder(
    val id: Long? = null,
    val name: String,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
