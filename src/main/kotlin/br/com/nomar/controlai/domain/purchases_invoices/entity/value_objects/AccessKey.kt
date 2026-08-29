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
    }

    @JsonValue
    fun asString(): String = value

    override fun toString() = value
}