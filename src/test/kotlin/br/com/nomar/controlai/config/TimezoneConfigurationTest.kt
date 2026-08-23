package br.com.nomar.controlai.config

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.env.Environment
import java.util.TimeZone

@SpringBootTest
class TimezoneConfigurationTest {

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var environment: Environment

    @Test
    fun `jackson serializes and deserializes using UTC regardless of host default timezone`() {
        assertEquals(TimeZone.getTimeZone("UTC"), objectMapper.serializationConfig.timeZone)
        assertEquals(TimeZone.getTimeZone("UTC"), objectMapper.deserializationConfig.timeZone)
    }

    @Test
    fun `hibernate jdbc time zone is fixed to UTC via configuration`() {
        assertEquals("UTC", environment.getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
    }
}
