package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

class Cnpj private constructor(val value: String) {

    companion object {
        private val REGEX = Regex("\\d{2}\\.\\d{3}\\.\\d{3}/\\d{4}-\\d{2}")

        @JvmStatic
        @JsonCreator
        fun of(value: String): Cnpj {
            require(REGEX.matches(value)) {
                "CNPJ deve estar no formato 00.000.000/0000-00"
            }
            return Cnpj(value)
        }
    }

    @JsonValue
    fun asString(): String = value

    override fun toString() = value
}