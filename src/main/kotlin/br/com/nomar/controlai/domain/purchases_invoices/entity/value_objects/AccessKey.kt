package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

class AccessKey private constructor(val value: String) {

    companion object {
        private val REGEX = Regex("\\d{44}")

        @JvmStatic
        @JsonCreator
        fun of(value: String): AccessKey {
            require(REGEX.matches(value)) {
                "AccessKey deve conter exatamente 44 dígitos numéricos"
            }
            return AccessKey(value)
        }

        @JvmStatic
        fun fromInvoiceUrl(invoiceUrl: InvoiceUrl): AccessKey {
            val url = invoiceUrl.asString()
            val queryStart = url.indexOf('?')
            require(queryStart >= 0) { "Invoice URL não contém query string" }

            val pValue = url.substring(queryStart + 1)
                .split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.getOrNull(0) == "p" }
                ?.getOrNull(1)
                ?: throw IllegalArgumentException("Invoice URL não contém o parâmetro 'p'")

            return of(pValue.substringBefore('|'))
        }
    }

    @JsonValue
    fun asString(): String = value

    override fun toString() = value
}