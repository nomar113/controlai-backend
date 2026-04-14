package br.com.nomar.controlai.application.installments.entrypoint.rest.request

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate

data class InstallmentPreviewRequest(
    @field:NotNull
    @field:DecimalMin("0.01")
    val totalAmount: BigDecimal,

    @field:NotNull
    @field:Min(2)
    val numberOfInstallments: Int,

    @field:NotNull
    val startDate: LocalDate,
)
