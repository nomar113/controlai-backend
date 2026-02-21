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
            URI.create(value)
            return InvoiceUrl(value)
        }
    }

    @JsonValue
    fun asString() = value
}