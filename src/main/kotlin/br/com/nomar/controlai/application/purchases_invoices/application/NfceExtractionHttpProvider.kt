package br.com.nomar.controlai.application.purchases_invoices.application

import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseInvoice
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.ExtractedPurchasePayment
import br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects.InvoiceUrl
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionBlockedException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionNavigationException
import br.com.nomar.controlai.domain.purchases_invoices.exception.NfceExtractionTimeoutException
import br.com.nomar.controlai.domain.purchases_invoices.gateway.NfceExtractionGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import java.math.BigDecimal

@Component
class NfceExtractionHttpProvider(
    restClientBuilder: RestClient.Builder,
    @Value("\${nfce-extraction.base-url}") private val baseUrl: String,
    @Value("\${nfce-extraction.internal-key}") private val internalKey: String,
) : NfceExtractionGateway {

    private val restClient = restClientBuilder.build()

    override fun extract(invoiceUrl: InvoiceUrl): Result<ExtractedPurchaseInvoice> = runCatching {
        val startedAt = System.currentTimeMillis()

        val result = try {
            restClient.post()
                .uri("$baseUrl/extract")
                .header(INTERNAL_KEY_HEADER, internalKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(ExtractionRequestDto(invoiceUrl.asString()))
                .retrieve()
                .body(ExtractionResultDto::class.java)
        } catch (ex: RestClientException) {
            val durationMs = System.currentTimeMillis() - startedAt
            logger.warn("Extracao de nota falhou status=ERROR durationMs={}", durationMs)
            throw NfceExtractionNavigationException(cause = ex)
        }

        val durationMs = System.currentTimeMillis() - startedAt

        when (result?.status) {
            ExtractionStatusDto.READY -> {
                logger.info("Extracao de nota concluida status={} durationMs={}", result.status, durationMs)
                result.data?.toDomain() ?: throw NfceExtractionNavigationException()
            }
            ExtractionStatusDto.BLOCKED -> {
                logger.warn("Extracao de nota concluida status={} durationMs={}", result.status, durationMs)
                throw NfceExtractionBlockedException(result.message ?: "SEFAZ bloqueou a consulta a nota")
            }
            ExtractionStatusDto.TIMEOUT -> {
                logger.warn("Extracao de nota concluida status={} durationMs={}", result.status, durationMs)
                throw NfceExtractionTimeoutException()
            }
            ExtractionStatusDto.NAVIGATION_ERROR, null -> {
                logger.warn("Extracao de nota concluida status={} durationMs={}", result?.status, durationMs)
                throw NfceExtractionNavigationException()
            }
        }
    }

    private data class ExtractionRequestDto(val invoiceUrl: String)

    private enum class ExtractionStatusDto { READY, BLOCKED, TIMEOUT, NAVIGATION_ERROR }

    private data class ExtractionResultDto(
        val status: ExtractionStatusDto,
        val data: ExtractedInvoiceDto? = null,
        val message: String? = null,
    )

    private data class ExtractedInvoiceDto(
        val merchantName: String,
        val cnpj: String,
        val merchantAddress: String,
        val totalItems: Int,
        val subtotal: BigDecimal,
        val discount: BigDecimal,
        val total: BigDecimal,
        val taxes: BigDecimal,
        val date: String,
        val items: List<ExtractedInvoiceItemDto>,
        val payments: List<ExtractedInvoicePaymentDto>,
    ) {
        fun toDomain() = ExtractedPurchaseInvoice(
            merchantName = merchantName,
            cnpj = cnpj,
            merchantAddress = merchantAddress,
            totalItems = totalItems,
            subtotal = subtotal,
            discount = discount,
            total = total,
            taxes = taxes,
            date = date,
            items = items.map { it.toDomain() },
            payments = payments.map { it.toDomain() },
        )
    }

    private data class ExtractedInvoiceItemDto(
        val productName: String,
        val code: String,
        val quantity: BigDecimal,
        val unit: String,
        val unitPrice: BigDecimal,
        val totalPrice: BigDecimal,
    ) {
        fun toDomain() = ExtractedPurchaseItem(productName, code, quantity, unit, unitPrice, totalPrice)
    }

    private data class ExtractedInvoicePaymentDto(
        val type: String,
        val value: BigDecimal,
    ) {
        fun toDomain() = ExtractedPurchasePayment(type, value)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(NfceExtractionHttpProvider::class.java)
        private const val INTERNAL_KEY_HEADER = "X-Internal-Key"
    }
}
