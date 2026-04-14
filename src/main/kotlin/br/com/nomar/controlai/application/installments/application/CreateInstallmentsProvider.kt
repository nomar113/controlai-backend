package br.com.nomar.controlai.application.installments.application

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
) {
    fun calculate(
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
    ): List<InstallmentPreviewItemResponse> {
        val baseAmount = totalAmount.divide(BigDecimal(totalInstallments), 2, RoundingMode.DOWN)
        val remainder = totalAmount.subtract(baseAmount.multiply(BigDecimal(totalInstallments)))

        return (1..totalInstallments).map { number ->
            InstallmentPreviewItemResponse(
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = if (number == 1) baseAmount.add(remainder) else baseAmount,
                dueDate = calculateDueDate(startDate, number),
            )
        }
    }

    fun execute(
        parentId: Long,
        totalInstallments: Int,
        totalAmount: BigDecimal,
        startDate: LocalDate,
    ): List<Installment> {
        val previews = calculate(totalInstallments, totalAmount, startDate)
        val installments = previews.map { preview ->
            Installment(
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
    ): List<Installment> {
        val installments = (1..totalInstallments).map { number ->
            Installment(
                parentId = parentId,
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = amounts[number]!!,
                dueDate = calculateDueDate(startDate, number),
            )
        }
        return installmentRepository.saveAll(installments)
    }

    companion object {
        fun calculateDueDate(startDate: LocalDate, installmentNumber: Int): LocalDate {
            val targetMonth = startDate.plusMonths((installmentNumber - 1).toLong())
            val dayOfMonth = minOf(startDate.dayOfMonth, targetMonth.lengthOfMonth())
            return targetMonth.withDayOfMonth(dayOfMonth)
        }
    }
}
