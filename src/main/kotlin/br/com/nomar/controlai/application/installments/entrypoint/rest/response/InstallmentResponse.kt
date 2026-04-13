package br.com.nomar.controlai.application.installments.entrypoint.rest.response

import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import java.math.BigDecimal
import java.time.LocalDate

data class InstallmentResponse(
    val id: Long,
    val parentId: Long,
    val installmentNumber: Int,
    val totalInstallments: Int,
    val amount: BigDecimal,
    val dueDate: LocalDate,
    val cancelled: Boolean,
) {
    companion object {
        fun from(installment: Installment) = InstallmentResponse(
            id = installment.id,
            parentId = installment.parentId,
            installmentNumber = installment.installmentNumber,
            totalInstallments = installment.totalInstallments,
            amount = installment.amount,
            dueDate = installment.dueDate,
            cancelled = installment.cancelledAt != null,
        )
    }
}

data class MonthlyProjectionResponse(
    val year: Int,
    val month: Int,
    val total: BigDecimal,
    val count: Int,
)
