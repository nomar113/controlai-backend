package br.com.nomar.controlai.application.nfce_processor.entrypoint.rest.request

import br.com.nomar.controlai.domain.purchases_invoices.entity.InvoiceReadData
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchaseItem
import br.com.nomar.controlai.domain.purchases_invoices.entity.PurchasePayment
import jakarta.validation.Valid
import jakarta.validation.constraints.Digits
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.PositiveOrZero
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.OffsetDateTime

// Malformed payloads (missing fields, wrong types, out of the column limits) answer 400 here; the
// consistency rules (empty items, sums, CNPJ, missing issuedAt) answer 422 from InvoiceReadResult.of
data class InvoiceReadResultRequest(
    @field:Pattern(regexp = "\\d{44}")
    val accessKey: String,
    @field:NotBlank @field:Size(max = TEXT_MAX)
    val merchantName: String,
    val cnpj: String,
    @field:Size(max = TEXT_MAX)
    val merchantAddress: String,
    val totalItems: Int,
    @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
    val subtotal: BigDecimal,
    @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
    val discount: BigDecimal,
    @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
    val taxes: BigDecimal,
    @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
    val total: BigDecimal,
    @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
    val change: BigDecimal,
    // e.g. "2026-10-20T14:32:10-03:00"
    val issuedAt: OffsetDateTime?,
    @field:Valid @field:Size(max = MAX_ITEMS)
    val items: List<Item>,
    @field:Valid @field:Size(max = MAX_PAYMENTS)
    val payments: List<Payment>,
) {

    data class Item(
        @field:NotBlank @field:Size(max = TEXT_MAX)
        val productName: String,
        @field:Size(max = TEXT_MAX)
        val code: String,
        @field:Positive @field:Digits(integer = QUANTITY_INTEGER_DIGITS, fraction = QUANTITY_FRACTION_DIGITS)
        val quantity: BigDecimal,
        @field:Size(max = TEXT_MAX)
        val unit: String,
        // The unit price may have more decimals than the column; the provider rounds it to cents
        @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = UNIT_PRICE_FRACTION_DIGITS)
        val unitPrice: BigDecimal,
        @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
        val totalPrice: BigDecimal,
    ) {
        fun toEntity() = PurchaseItem(productName, code, quantity, unit, unitPrice, totalPrice)
    }

    data class Payment(
        @field:NotBlank @field:Size(max = TEXT_MAX)
        val type: String,
        @field:PositiveOrZero @field:Digits(integer = MONEY_INTEGER_DIGITS, fraction = MONEY_FRACTION_DIGITS)
        val value: BigDecimal,
    ) {
        fun toEntity() = PurchasePayment(type, value)
    }

    fun toReadData() = InvoiceReadData(
        accessKey = accessKey,
        merchantName = merchantName,
        cnpj = cnpj,
        merchantAddress = merchantAddress,
        totalItems = totalItems,
        subtotal = subtotal,
        discount = discount,
        taxes = taxes,
        total = total,
        change = change,
        issuedAt = issuedAt,
        items = items.map(Item::toEntity),
        payments = payments.map(Payment::toEntity),
    )

    // Limits of the purchase_invoices, purchase_items and purchase_payments columns, and of the NFC-e
    // layout (at most 990 items and 100 payments), so the open endpoint never takes an unbounded payload
    private companion object {
        const val MAX_ITEMS = 990
        const val MAX_PAYMENTS = 100
        const val TEXT_MAX = 255
        const val MONEY_INTEGER_DIGITS = 8
        const val MONEY_FRACTION_DIGITS = 2
        const val UNIT_PRICE_FRACTION_DIGITS = 10
        const val QUANTITY_INTEGER_DIGITS = 7
        const val QUANTITY_FRACTION_DIGITS = 3
    }
}
