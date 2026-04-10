package br.com.nomar.controlai.application.categories

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
class CategoryControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("DELETE FROM categories")
    }

    @Test
    fun `POST categories should create category and return 201`() {
        mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Alimentacao"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Alimentacao"))
            .andExpect(jsonPath("$.id").isNumber)
    }

    @Test
    fun `GET categories should list categories in alphabetical order`() {
        createCategory("Transporte")
        createCategory("Alimentacao")
        createCategory("Moradia")

        mockMvc.perform(get("/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].name").value("Alimentacao"))
            .andExpect(jsonPath("$[1].name").value("Moradia"))
            .andExpect(jsonPath("$[2].name").value("Transporte"))
    }

    @Test
    fun `GET categories by id should return category`() {
        val id = createCategory("Mercado")

        mockMvc.perform(get("/categories/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Mercado"))
    }

    @Test
    fun `GET categories by invalid id should return 404`() {
        mockMvc.perform(get("/categories/99999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT categories should update category name`() {
        val id = createCategory("Old Name")

        mockMvc.perform(
            put("/categories/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "New Name"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
    }

    @Test
    fun `PUT categories with invalid id should return 404`() {
        mockMvc.perform(
            put("/categories/99999")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "X"}""")
        )
            .andExpect(status().isNotFound)
    }

    @Test
    fun `DELETE categories should return 204 and soft delete`() {
        val id = createCategory("To Delete")

        mockMvc.perform(delete("/categories/$id"))
            .andExpect(status().isNoContent)

        // Should not appear in listing (soft deleted)
        mockMvc.perform(get("/categories"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST categories with duplicate name should return 409`() {
        createCategory("Mercado")

        mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Mercado"}""")
        )
            .andExpect(status().isConflict)
    }

    @Test
    fun `POST categories with blank name should return 400`() {
        mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST categories with name exceeding 50 chars should return 400`() {
        val longName = "A".repeat(51)
        mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$longName"}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `DELETE categories linked to purchases should return 409`() {
        val categoryId = createCategory("Vinculada")

        jdbcTemplate.update(
            """INSERT INTO payment_notifications
               (card_last_digits, purchased_at, amount, merchant_name, number_of_installments, origin, origin_type, category_id)
               VALUES (?, CURRENT_TIMESTAMP, ?, ?, ?, ?, ?, ?)""",
            "1111", 100.00, "Test Store", 1, "MANUAL", "MANUAL", categoryId
        )

        mockMvc.perform(delete("/categories/$categoryId"))
            .andExpect(status().isConflict)

        // Cleanup
        jdbcTemplate.update("UPDATE payment_notifications SET category_id = NULL WHERE card_last_digits = '1111'")
        jdbcTemplate.update("DELETE FROM payment_notifications WHERE card_last_digits = '1111'")
    }

    @Test
    fun `full CRUD cycle should work`() {
        // Create
        val id = createCategory("Teste CRUD")

        // Read
        mockMvc.perform(get("/categories/$id"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Teste CRUD"))

        // Update
        mockMvc.perform(
            put("/categories/$id")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Teste Atualizado"}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Teste Atualizado"))

        // Delete
        mockMvc.perform(delete("/categories/$id"))
            .andExpect(status().isNoContent)

        // Verify deleted
        mockMvc.perform(get("/categories/$id"))
            .andExpect(status().isNotFound)
    }

    // --- Helpers ---

    private fun createCategory(name: String): Long {
        val response = mockMvc.perform(
            post("/categories")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andReturn().response.contentAsString

        return objectMapper.readTree(response).get("id").asLong()
    }
}
