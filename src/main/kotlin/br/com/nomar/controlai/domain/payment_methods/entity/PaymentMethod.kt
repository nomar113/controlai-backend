package br.com.nomar.controlai.domain.payment_methods.entity

import java.time.LocalDateTime

class PaymentMethod(
    val id: Long? = null,
    val name: String,
    val type: PaymentMethodType,
    val holderId: Long,
    val holder: Holder? = null,
    val brand: String? = null,
    val closingDay: Int? = null,
    val subCards: List<SubCard> = emptyList(),
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
