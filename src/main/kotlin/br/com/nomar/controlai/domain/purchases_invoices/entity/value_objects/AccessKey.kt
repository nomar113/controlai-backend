package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URLDecoder

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

            val rawPValue = url.substring(queryStart + 1)
                .split("&")
                .map { it.split("=", limit = 2) }
                .firstOrNull { it.getOrNull(0) == "p" }
                ?.getOrNull(1)
                ?: throw IllegalArgumentException("Invoice URL não contém o parâmetro 'p'")

            // Some scanners/readers percent-encode the '|' separator (e.g. "%7C") instead of
            // sending it raw, so the value must be decoded before splitting on it.
            val pValue = URLDecoder.decode(rawPValue, Charsets.UTF_8)

            return of(pValue.substringBefore('|'))
        }
    }

    @JsonValue
    fun asString(): String = value

    override fun toString() = value
}