package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URI

class InvoiceUrl private constructor(val value: String) {

    companion object {
        @JvmStatic
        @JsonCreator
        fun of(value: String): InvoiceUrl {
            require(value.isNotBlank()) { "Invoice URL não pode ser vazia" }
            val normalizedValue = normalizeQueryForValidation(value)
            URI.create(normalizedValue)
            return InvoiceUrl(value)
        }

        private fun normalizeQueryForValidation(url: String): String {
            val queryStart = url.indexOf('?')
            if (queryStart < 0) return url

            val fragmentStart = url.indexOf('#', queryStart + 1)
            val queryEnd = if (fragmentStart >= 0) fragmentStart else url.length

            val prefix = url.take(queryStart + 1)
            val query = url.substring(queryStart + 1, queryEnd)
            val suffix = if (fragmentStart >= 0) url.substring(fragmentStart) else ""

            val normalizedQuery = query
                .replace("|", "%7C")
                .replace(" ", "%20")

            return "$prefix$normalizedQuery$suffix"
        }
    }

    @JsonValue
    fun asString() = value
}
