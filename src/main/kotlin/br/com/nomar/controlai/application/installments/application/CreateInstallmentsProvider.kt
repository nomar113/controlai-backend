package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentPreviewItemResponse
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class CreateInstallmentsProvider(
    private val installmentRepository: InstallmentRepository,
    private val budgetPeriodResolver: BudgetPeriodResolver,
) {
    fun calculate(
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
        groupId: Long,
        paymentMethodId: Long? = null,
    ): List<InstallmentPreviewItemResponse> {
        val baseAmount = totalAmount.divide(BigDecimal(totalInstallments), 2, RoundingMode.DOWN)
        val remainder = totalAmount.subtract(baseAmount.multiply(BigDecimal(totalInstallments)))

        return (1..totalInstallments).map { number ->
            InstallmentPreviewItemResponse(
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = if (number == 1) baseAmount.add(remainder) else baseAmount,
                dueDate = budgetPeriodResolver.resolveInstallmentDueDate(startDate, paymentMethodId, number, groupId),
            )
        }
    }

    fun execute(
        parentId: Long,
        groupId: Long,
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
        paymentMethodId: Long? = null,
    ): List<Installment> {
        val previews = calculate(totalInstallments, totalAmount, startDate, groupId, paymentMethodId)
        val installments = previews.map { preview ->
            Installment(
                groupId = groupId,
                parentId = parentId,
                installmentNumber = preview.installmentNumber,
                totalInstallments = preview.totalInstallments,
                amount = preview.amount,
                dueDate = preview.dueDate,
            )
        }
        return installmentRepository.saveAll(installments)
    }

    fun executeWithAmounts(
        parentId: Long,
        groupId: Long,
        totalInstallments: Int,
        amounts: Map<Int, BigDecimal>,
        startDate: LocalDate,
        paymentMethodId: Long? = null,
    ): List<Installment> {
        val installments = (1..totalInstallments).map { number ->
            Installment(
                groupId = groupId,
                parentId = parentId,
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = amounts[number]!!,
                dueDate = budgetPeriodResolver.resolveInstallmentDueDate(startDate, paymentMethodId, number, groupId),
            )
        }
        return installmentRepository.saveAll(installments)
    }
}
