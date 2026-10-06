package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.model.PurchaseInvoiceModel
import br.com.nomar.controlai.application.purchases_invoices.entrypoint.database.repository.PurchaseInvoiceRepository
import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceStatus
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.NfceQrCode
import br.com.nomar.controlai.domain.purchases_invoices.exception.DuplicateInvoiceException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.RegisterPendingInvoiceGateway
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Component
class RegisterPendingInvoiceProvider(
    private val purchaseInvoiceRepository: PurchaseInvoiceRepository,
    private val transactionTemplate: TransactionTemplate,
) : RegisterPendingInvoiceGateway {

    // TransactionTemplate instead of @Transactional: the unique index violation must roll the
    // transaction back before it is translated, otherwise returning Result.failure from a
    // rollback-only @Transactional method would end in UnexpectedRollbackException on commit
    override fun execute(qrCode: NfceQrCode, groupId: Long, scannedAt: Instant): Result<PurchaseInvoiceModel> {
        return runCatching {
            try {
                transactionTemplate.execute { save(qrCode, groupId, scannedAt) }!!
            } catch (ex: DataIntegrityViolationException) {
                // A concurrent scan of the same key in the group won the race past the exists check
                if (ex.mostSpecificCause.message.orEmpty().contains(ACTIVE_KEY_INDEX)) throw DuplicateInvoiceException()
                throw ex
            }
        }
    }

    private fun save(qrCode: NfceQrCode, groupId: Long, scannedAt: Instant): PurchaseInvoiceModel {
        val accessKey = qrCode.accessKey.value
        if (purchaseInvoiceRepository.existsByGroupIdAndAccessKey(groupId, accessKey)) {
            throw DuplicateInvoiceException()
        }
        return purchaseInvoiceRepository.saveAndFlush(
            PurchaseInvoiceModel(
                groupId = groupId,
                date = scannedAt,
                merchantName = null,
                merchantAddress = null,
                cnpj = null,
                totalItems = null,
                invoiceUrl = qrCode.invoiceUrl.value,
                accessKey = accessKey,
                subtotal = null,
                total = null,
                taxes = null,
                discount = null,
                status = InvoiceStatus.PENDING,
            )
        )
    }

    companion object {
        const val ACTIVE_KEY_INDEX = "uk_purchase_invoices_group_active_key"
    }
}
