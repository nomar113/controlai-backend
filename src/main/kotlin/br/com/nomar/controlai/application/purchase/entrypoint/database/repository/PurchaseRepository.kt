package br.com.nomar.controlai.application.purchase.entrypoint.database.repository

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PurchaseRepository : JpaRepository<Purchase, Long>