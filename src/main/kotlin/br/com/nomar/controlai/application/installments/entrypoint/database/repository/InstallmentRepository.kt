package br.com.nomar.controlai.application.installments.entrypoint.database.repository

import br.com.nomar.controlai.application.installments.entrypoint.database.model.Installment
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface MonthlyProjection {
    fun getYear(): Int
    fun getMonth(): Int
    fun getTotal(): java.math.BigDecimal
    fun getCount(): Int
}

interface InstallmentRepository : JpaRepository<Installment, Long> {

    fun findByParentIdOrderByInstallmentNumber(parentId: Long): List<Installment>

    fun findByParentIdAndCancelledAtIsNull(parentId: Long): List<Installment>

    fun findByIdAndGroupId(id: Long, groupId: Long): Installment?

    @Query(
        value = """
            SELECT YEAR(i.due_date) AS year, MONTH(i.due_date) AS month,
                   SUM(i.amount) AS total, COUNT(i.id) AS count
            FROM installments i
            WHERE i.cancelled_at IS NULL
              AND i.group_id = :groupId
              AND i.due_date >= :startDate
              AND i.due_date < :endDate
            GROUP BY YEAR(i.due_date), MONTH(i.due_date)
            ORDER BY year, month
        """,
        nativeQuery = true,
    )
    fun getMonthlyProjection(
        @Param("groupId") groupId: Long,
        @Param("startDate") startDate: LocalDate,
        @Param("endDate") endDate: LocalDate,
    ): List<MonthlyProjection>
}
