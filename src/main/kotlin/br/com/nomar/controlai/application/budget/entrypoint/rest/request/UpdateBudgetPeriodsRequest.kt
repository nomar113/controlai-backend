package br.com.nomar.controlai.application.budget.entrypoint.rest.request

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import java.time.LocalDate

data class UpdateBudgetPeriodsRequest(
    @field:NotEmpty
    @field:Valid
    val periods: List<PeriodEntry>,
)

data class PeriodEntry(
    @field:NotNull
    val paymentMethodId: Long,

    @field:NotNull
    val startDate: LocalDate,

    @field:NotNull
    val endDate: LocalDate,
)
