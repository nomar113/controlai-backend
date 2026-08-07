package br.com.nomar.controlai.application.budget

import br.com.nomar.controlai.application.budget.application.BudgetPeriodCalculator
import br.com.nomar.controlai.application.budget.application.BudgetPeriodResolver
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetModel
import br.com.nomar.controlai.application.budget.entrypoint.database.model.BudgetPaymentPeriodModel
import br.com.nomar.controlai.application.budget.entrypoint.database.repository.BudgetRepository
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.PaymentMethodModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.PaymentMethodRepository
import br.com.nomar.controlai.domain.auth.RequestContext
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.time.LocalDate
import java.time.YearMonth
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BudgetPeriodResolverTest {

    private val budgetRepository: BudgetRepository = mock()
    private val paymentMethodRepository: PaymentMethodRepository = mock()
    private val requestContext: RequestContext = mock<RequestContext>().also { `when`(it.groupId).thenReturn(1L) }
    private val resolver = BudgetPeriodResolver(
        budgetRepository,
        paymentMethodRepository,
        BudgetPeriodCalculator(),
        requestContext,
    )

    private val yearMonth = YearMonth.of(2026, 5)

    private fun creditCard(id: Long, closingDay: Int = 10) = PaymentMethodModel(
        id = id,
        groupId = 1L,
        name = "Card $id",
        type = "CREDIT_CARD",
        holderId = 1L,
        closingDay = closingDay,
    )

    @Test
    fun `resolvePeriods computes dates in memory and never persists when no budget exists`() {
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-05", 1L)).thenReturn(Optional.empty())
        `when`(paymentMethodRepository.findAllByGroupIdOrderByNameAsc(1L)).thenReturn(listOf(creditCard(id = 42, closingDay = 10)))

        val periods = resolver.resolvePeriods(yearMonth)

        assertEquals(1, periods.size)
        assertEquals(42L, periods[0].paymentMethodId)
        assertEquals(LocalDate.of(2026, 4, 11), periods[0].startDate)
        assertEquals(LocalDate.of(2026, 5, 10), periods[0].endDate)
        verify(budgetRepository, never()).save(any(BudgetModel::class.java))
    }

    @Test
    fun `resolvePeriods returns persisted periods when budget already exists`() {
        val budget = BudgetModel(id = 1L, groupId = 1L, yearMonth = "2026-05")
        budget.paymentPeriods.add(
            BudgetPaymentPeriodModel(
                budget = budget,
                paymentMethodId = 42L,
                startDate = LocalDate.of(2026, 4, 10),
                endDate = LocalDate.of(2026, 5, 9),
            )
        )
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-05", 1L)).thenReturn(Optional.of(budget))
        `when`(paymentMethodRepository.findAllByGroupIdOrderByNameAsc(1L)).thenReturn(listOf(creditCard(id = 42)))

        val periods = resolver.resolvePeriods(yearMonth)

        assertEquals(1, periods.size)
        // manually-customized dates from the persisted period, NOT recalculated via closingDay
        assertEquals(LocalDate.of(2026, 4, 10), periods[0].startDate)
        assertEquals(LocalDate.of(2026, 5, 9), periods[0].endDate)
    }

    @Test
    fun `resolvePeriods self-heals a missing payment method on an existing budget`() {
        val budget = BudgetModel(id = 1L, groupId = 1L, yearMonth = "2026-05")
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-05", 1L)).thenReturn(Optional.of(budget))
        `when`(paymentMethodRepository.findAllByGroupIdOrderByNameAsc(1L)).thenReturn(listOf(creditCard(id = 99, closingDay = 5)))

        val periods = resolver.resolvePeriods(yearMonth)

        assertEquals(1, periods.size)
        assertEquals(99L, periods[0].paymentMethodId)
        verify(budgetRepository).save(budget)
    }

    @Test
    fun `ensurePaymentPeriodsSynced does not touch an already-synced budget`() {
        val budget = BudgetModel(id = 1L, groupId = 1L, yearMonth = "2026-05")
        budget.paymentPeriods.add(
            BudgetPaymentPeriodModel(
                budget = budget,
                paymentMethodId = 42L,
                startDate = LocalDate.of(2026, 4, 11),
                endDate = LocalDate.of(2026, 5, 10),
            )
        )
        `when`(paymentMethodRepository.findAllByGroupIdOrderByNameAsc(1L)).thenReturn(listOf(creditCard(id = 42)))

        resolver.ensurePaymentPeriodsSynced(budget, yearMonth)

        assertEquals(1, budget.paymentPeriods.size)
        verify(budgetRepository, never()).save(any(BudgetModel::class.java))
    }

    @Test
    fun `resolvePeriods excludes payment methods of unsupported types`() {
        `when`(budgetRepository.findByYearMonthAndGroupId("2026-05", 1L)).thenReturn(Optional.empty())
        `when`(paymentMethodRepository.findAllByGroupIdOrderByNameAsc(1L)).thenReturn(
            listOf(PaymentMethodModel(id = 1L, groupId = 1L, name = "Unsupported", type = "OTHER", holderId = 1L))
        )

        val periods = resolver.resolvePeriods(yearMonth)

        assertTrue(periods.isEmpty())
    }
}
