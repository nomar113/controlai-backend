package br.com.nomar.controlai.application.purchases_invoices

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class CancelPurchaseInvoiceControllerTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM purchase_invoices")
    }

    private fun insertInvoice(cancelledAt: String? = null): Long {
        jdbcTemplate.update(
            """INSERT INTO purchase_invoices (date, merchant_name, merchant_address, cnpj, total_items, subtotal, total, taxes, discount, cancelled_at, group_id)
               VALUES ('2024-01-15 10:00:00', 'Mercado Test', 'Rua A', '12345678000199', 3, 50.00, 50.00, 0.00, 0.00, ${if (cancelledAt != null) "'$cancelledAt'" else "NULL"}, 1)"""
        )
        return jdbcTemplate.queryForObject("SELECT MAX(id) FROM purchase_invoices", Long::class.java)!!
    }

    @Test
    fun `PATCH cancel should return 200 for valid invoice`() {
        val id = insertInvoice()

        mockMvc.perform(patch("/purchases/invoices/$id/cancel"))
            .andExpect(status().isOk)
    }

    @Test
    fun `PATCH cancel should return 404 for non-existent invoice`() {
        mockMvc.perform(patch("/purchases/invoices/999999/cancel"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PATCH cancel should return 409 for already cancelled invoice`() {
        val id = insertInvoice(cancelledAt = "2024-01-20 10:00:00")

        mockMvc.perform(patch("/purchases/invoices/$id/cancel"))
            .andExpect(status().isConflict)
    }
}
