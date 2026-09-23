package br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.SubCardModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface SubCardRepository : JpaRepository<SubCardModel, Long> {
    fun findAllByPaymentMethodId(paymentMethodId: Long): List<SubCardModel>

    // Active sub-cards with these last digits whose parent card belongs to the given group.
    // Scoping by group keeps a purchase from ever being linked to another group's card.
    @Query(
        """
        select s from SubCardModel s, PaymentMethodModel pm
        where pm.id = s.paymentMethodId and pm.groupId = :groupId
          and pm.deletedAt is null and s.deletedAt is null
          and s.lastFourDigits = :digits
        """,
    )
    fun findActiveByLastFourDigitsAndGroupId(
        @Param("digits") digits: String,
        @Param("groupId") groupId: Long,
    ): List<SubCardModel>
}
