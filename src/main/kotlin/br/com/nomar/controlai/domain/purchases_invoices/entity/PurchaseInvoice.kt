package br.com.nomar.controlai.domain.purchases_invoices.entity

import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.AccessKey
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.Cnpj
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.TotalItems
import com.fasterxml.jackson.annotation.JsonFormat
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime

class PurchaseInvoice(
    val id: Long? = null,
    @param:JsonFormat(pattern = "dd/MM/yyyy HH:mm:ssXXX")
    val date: Instant,
    val merchantName: String,
    val merchantAddress: String,
    val cnpj: Cnpj,
    val totalItems: TotalItems,
    val invoiceUrl: InvoiceUrl,
    val accessKey: AccessKey,
    val subtotal: BigDecimal,
    val total: BigDecimal,
    val taxes: BigDecimal,
    val discount: BigDecimal,
    val items: List<PurchaseItem> = emptyList(),
    val payments: List<PurchasePayment> = emptyList(),
    val createdAt: LocalDateTime? = null,
    val updatedAt: LocalDateTime? = null
)
