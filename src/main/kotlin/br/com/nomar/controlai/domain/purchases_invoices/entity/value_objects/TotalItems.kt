package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

class TotalItems private constructor(val value: Int) {

    companion object {

        @JvmStatic
        @JsonCreator
        fun of(value: Int): TotalItems {
            require(value > 0) {
                "totalItems deve ser um número inteiro maior que zero"
            }
            return TotalItems(value)
        }
    }

    @JsonValue
    fun asInt(): Int = value

    override fun toString() = value.toString()
}