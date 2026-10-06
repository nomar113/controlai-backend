package br.com.nomar.controlai.domain.purchases_invoices.gateway

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.NfceQrCode
import java.time.Instant

fun interface RegisterPendingInvoiceGateway {
    // Failure: DuplicateInvoiceException when the group already has an active invoice with this key
    fun execute(qrCode: NfceQrCode, groupId: Long, scannedAt: Instant): Result<PurchaseInvoiceModel>
}
