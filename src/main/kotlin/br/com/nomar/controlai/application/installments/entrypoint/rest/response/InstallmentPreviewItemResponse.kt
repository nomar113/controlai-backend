package br.com.nomar.controlai.application.installments.entrypoint.rest.response

import java.math.BigDecimal
import java.time.LocalDate

data class InstallmentPreviewItemResponse(
    val installmentNumber: Int,
    val totalInstallments: Int,
    val amount: BigDecimal,
    val dueDate: LocalDate,
)
