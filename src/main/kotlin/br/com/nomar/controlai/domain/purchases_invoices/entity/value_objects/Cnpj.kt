package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

class Cnpj private constructor(val value: String) {

    companion object {
        // Formatted as printed by the SEFAZ page. Since July 2026 the 12 base characters may be
        // letters (alphanumeric CNPJ); the 2 check digits stay numeric.
        private val REGEX = Regex("[A-Z0-9]{2}\\.[A-Z0-9]{3}\\.[A-Z0-9]{3}/[A-Z0-9]{4}-\\d{2}")

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
