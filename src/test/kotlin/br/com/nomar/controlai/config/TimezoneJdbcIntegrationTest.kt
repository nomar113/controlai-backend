package br.com.nomar.controlai.config

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import java.sql.Timestamp
import java.time.Instant
import java.util.TimeZone

/**
 * Valida contra o MySQL real do docker-compose que a sessão JDBC é forçada para UTC
 * (connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true), independentemente
 * do timezone default do container MySQL ou da JVM que roda a aplicação.
 */
@SpringBootTest
class TimezoneJdbcIntegrationTest {

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute(
            "CREATE TABLE IF NOT EXISTS timezone_probe (id BIGINT AUTO_INCREMENT PRIMARY KEY, ts TIMESTAMP(3) NOT NULL)"
        )
        jdbcTemplate.update("DELETE FROM timezone_probe")
    }

    @AfterEach
    fun tearDown() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS timezone_probe")
    }

    @Test
    fun `mysql session time_zone is forced to UTC regardless of the container default`() {
        val sessionTimeZone = jdbcTemplate.queryForObject("SELECT @@session.time_zone", String::class.java)
        assertEquals("+00:00", sessionTimeZone)
    }

    @ParameterizedTest
    @ValueSource(strings = ["UTC", "America/Sao_Paulo"])
    fun `a known instant round-trips identically regardless of the JVM default timezone`(zoneId: String) {
        val originalDefault = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zoneId))

            val known: Instant = Instant.parse("2026-01-15T23:30:00Z")
            jdbcTemplate.update("INSERT INTO timezone_probe (ts) VALUES (?)", Timestamp.from(known))

            val readBack = jdbcTemplate.queryForObject("SELECT ts FROM timezone_probe ORDER BY id DESC LIMIT 1") { rs, _ ->
                rs.getTimestamp("ts").toInstant()
            }

            assertEquals(known, readBack)
        } finally {
            TimeZone.setDefault(originalDefault)
        }
    }
}
