package br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.PaymentMethodModel
import org.springframework.data.jpa.repository.JpaRepository

interface PaymentMethodRepository : JpaRepository<PaymentMethodModel, Long> {
    fun findAllByHolderIdOrderByNameAsc(holderId: Long): List<PaymentMethodModel>
    fun findAllByOrderByNameAsc(): List<PaymentMethodModel>
    fun findAllByGroupIdOrderByNameAsc(groupId: Long): List<PaymentMethodModel>
    fun findAllByGroupIdAndHolderIdOrderByNameAsc(groupId: Long, holderId: Long): List<PaymentMethodModel>
    fun findByIdAndGroupId(id: Long, groupId: Long): PaymentMethodModel?
}
