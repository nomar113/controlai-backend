package br.com.nomar.controlai.application.payment_methods

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
class ControllerIntegrationTest {

    @Autowired private lateinit var mockMvc: MockMvc
    @Autowired private lateinit var objectMapper: ObjectMapper
    @Autowired private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun cleanUp() {
        jdbcTemplate.update("UPDATE payment_notifications SET payment_method_id = NULL, sub_card_id = NULL")
        jdbcTemplate.update("DELETE FROM sub_cards")
        jdbcTemplate.update("DELETE FROM payment_methods")
        jdbcTemplate.update("DELETE FROM holders")
    }

    @Test
    fun `POST holders should create holder and return 201`() {
        mockMvc.perform(
            post("/holders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "Ramon"}""")
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Ramon"))
            .andExpect(jsonPath("$.id").isNumber)
    }

    @Test
    fun `GET holders should list holders`() {
        createHolder("Aline")
        createHolder("Ramon")

        mockMvc.perform(get("/holders"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Aline"))
            .andExpect(jsonPath("$[1].name").value("Ramon"))
    }

    @Test
    fun `POST payment-methods should create card with sub-cards and return 201`() {
        val holderId = createHolder("Ramon")

        mockMvc.perform(
            post("/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "Smiles Infinite",
                        "type": "CREDIT_CARD",
                        "holderId": $holderId,
                        "brand": "Visa",
                        "closingDay": 15,
                        "subCards": [
                            {"lastFourDigits": "6668", "type": "PHYSICAL_HOLDER"}
                        ]
                    }
                """.trimIndent())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Smiles Infinite"))
            .andExpect(jsonPath("$.type").value("CREDIT_CARD"))
            .andExpect(jsonPath("$.brand").value("Visa"))
            .andExpect(jsonPath("$.closingDay").value(15))
            .andExpect(jsonPath("$.subCards.length()").value(1))
            .andExpect(jsonPath("$.subCards[0].lastFourDigits").value("6668"))
    }

    @Test
    fun `GET payment-methods should list cards with sub-cards`() {
        val holderId = createHolder("Ramon")
        createPaymentMethod(holderId, "Smiles", "CREDIT_CARD")

        mockMvc.perform(get("/payment-methods"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Smiles"))
            .andExpect(jsonPath("$[0].holder.name").value("Ramon"))
    }

    @Test
    fun `GET payment-methods with holderId filter should return filtered results`() {
        val ramonId = createHolder("Ramon")
        val alineId = createHolder("Aline")
        createPaymentMethod(ramonId, "Smiles", "CREDIT_CARD")
        createPaymentMethod(alineId, "Nubank", "CREDIT_CARD")

        mockMvc.perform(get("/payment-methods").param("holderId", ramonId.toString()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Smiles"))
    }

    @Test
    fun `GET payment-methods by id should return card with details`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles", "CREDIT_CARD")

        mockMvc.perform(get("/payment-methods/$pmId"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Smiles"))
            .andExpect(jsonPath("$.holder.name").value("Ramon"))
    }

    @Test
    fun `GET payment-methods by invalid id should return 404`() {
        mockMvc.perform(get("/payment-methods/99999"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `PUT payment-methods should update card`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Old Name", "CREDIT_CARD")

        mockMvc.perform(
            put("/payment-methods/$pmId")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "name": "New Name",
                        "type": "CREDIT_CARD",
                        "holderId": $holderId,
                        "brand": "Mastercard"
                    }
                """.trimIndent())
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("New Name"))
            .andExpect(jsonPath("$.brand").value("Mastercard"))
    }

    @Test
    fun `DELETE payment-methods should return 204`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "To Delete", "PIX")

        mockMvc.perform(delete("/payment-methods/$pmId"))
            .andExpect(status().isNoContent)

        // Should not appear in listing
        mockMvc.perform(get("/payment-methods"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `POST sub-cards should create sub-card and return 201`() {
        val holderId = createHolder("Ramon")
        val pmId = createPaymentMethod(holderId, "Smiles", "CREDIT_CARD")

        mockMvc.perform(
            post("/payment-methods/$pmId/sub-cards")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                        "lastFourDigits": "9687",
                        "type": "PHYSICAL_DEPENDENT",
                        "dependentName": "Aline"
                    }
                """.trimIndent())
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.lastFourDigits").value("9687"))
            .andExpect(jsonPath("$.type").value("PHYSICAL_DEPENDENT"))
            .andExpect(jsonPath("$.dependentName").value("Aline"))
    }

    @Test
    fun `POST holders with blank name should return 400`() {
        mockMvc.perform(
            post("/holders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `POST payment-methods with missing required fields should return 400`() {
        mockMvc.perform(
            post("/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": ""}""")
        )
            .andExpect(status().isBadRequest)
    }

    // --- Helpers ---

    private fun createHolder(name: String): Long {
        val response = mockMvc.perform(
            post("/holders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name"}""")
        ).andReturn().response.contentAsString

        return objectMapper.readTree(response).get("id").asLong()
    }

    private fun createPaymentMethod(holderId: Long, name: String, type: String): Long {
        val response = mockMvc.perform(
            post("/payment-methods")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name": "$name", "type": "$type", "holderId": $holderId}""")
        ).andReturn().response.contentAsString

        return objectMapper.readTree(response).get("id").asLong()
    }
}
