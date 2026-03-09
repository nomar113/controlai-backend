package br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PurchaseInvoiceRepository : JpaRepository<PurchaseInvoiceModel, Long> {

    fun countByAccessKey(accessKey: String): Long

    fun findAllByOrderByDateDesc(): List<PurchaseInvoiceModel>
}
