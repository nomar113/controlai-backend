package br.com.nomar.controlai.domain.payment_methods.entity

import java.time.LocalDateTime

class SubCard(
    val id: Long? = null,
    val paymentMethodId: Long,
    val lastFourDigits: String,
    val type: SubCardType,
    val nickname: String? = null,
    val dependentName: String? = null,
    val walletPlatform: WalletPlatform? = null,
    val deletedAt: LocalDateTime? = null,
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null,
)
