package br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.SubCardModel
import org.springframework.data.jpa.repository.JpaRepository

interface SubCardRepository : JpaRepository<SubCardModel, Long> {
    fun findAllByPaymentMethodId(paymentMethodId: Long): List<SubCardModel>
}
