package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.budget.application.BudgetPeriodCalculator
import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import br.com.nomar.controlai.application.installments.entrypoint.rest.response.InstallmentPreviewItemResponse
import br.com.nomar.controlai.domain.auth.RequestContext
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class CreateInstallmentsProvider(
    private val installmentRepository: InstallmentRepository,
    private val requestContext: RequestContext,
    private val periodCalculator: BudgetPeriodCalculator,
) {
    fun calculate(
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
        closingDay: Int? = null,
        type: String = "OTHER",
    ): List<InstallmentPreviewItemResponse> {
        val baseAmount = totalAmount.divide(BigDecimal(totalInstallments), 2, RoundingMode.DOWN)
        val remainder = totalAmount.subtract(baseAmount.multiply(BigDecimal(totalInstallments)))

        return (1..totalInstallments).map { number ->
            InstallmentPreviewItemResponse(
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = if (number == 1) baseAmount.add(remainder) else baseAmount,
                dueDate = periodCalculator.resolveInstallmentDueDate(startDate, closingDay, type, number),
            )
        }
    }

    fun execute(
        parentId: Long,
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
        closingDay: Int? = null,
        type: String = "OTHER",
    ): List<Installment> {
        val groupId = requestContext.groupId
        val previews = calculate(totalInstallments, totalAmount, startDate, closingDay, type)
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
        totalInstallments: Int,
        amounts: Map<Int, BigDecimal>,
        startDate: LocalDate,
        closingDay: Int? = null,
        type: String = "OTHER",
    ): List<Installment> {
        val groupId = requestContext.groupId
        val installments = (1..totalInstallments).map { number ->
            Installment(
                groupId = groupId,
                parentId = parentId,
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = amounts[number]!!,
                dueDate = periodCalculator.resolveInstallmentDueDate(startDate, closingDay, type, number),
            )
        }
        return installmentRepository.saveAll(installments)
    }
}
