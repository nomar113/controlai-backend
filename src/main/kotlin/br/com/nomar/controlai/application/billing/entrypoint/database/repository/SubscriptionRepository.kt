package br.com.nomar.controlai.application.billing.entrypoint.database.repository

import br.com.nomar.controlai.application.billing.entrypoint.database.model.SubscriptionModel
import org.springframework.data.jpa.repository.JpaRepository

interface SubscriptionRepository : JpaRepository<SubscriptionModel, Long> {
    fun findByGroupId(groupId: Long): SubscriptionModel?
}
