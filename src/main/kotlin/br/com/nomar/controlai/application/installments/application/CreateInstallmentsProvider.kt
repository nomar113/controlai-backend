package br.com.nomar.controlai.application.installments.application

import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import br.com.nomar.controlai.application.installments.entrypoint.database.repository.InstallmentRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

@Service
class CreateInstallmentsProvider(
    private val installmentRepository: InstallmentRepository,
) {
    fun execute(
        parentId: Long,
        totalInstallments: Int,
        amount: BigDecimal,
        startDate: LocalDate,
    ): List<Installment> {
        val installments = (1..totalInstallments).map { number ->
            Installment(
                parentId = parentId,
                installmentNumber = number,
                totalInstallments = totalInstallments,
                amount = amount,
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
