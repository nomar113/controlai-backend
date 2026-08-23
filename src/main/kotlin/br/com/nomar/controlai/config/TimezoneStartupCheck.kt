package br.com.nomar.controlai.config

import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class TimezoneStartupCheck(
    private val jdbcTemplate: JdbcTemplate,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(TimezoneStartupCheck::class.java)
        private const val EXPECTED_SESSION_TIME_ZONE = "+00:00"
    }

    @EventListener(ApplicationReadyEvent::class)
    fun logSessionTimeZoneMismatch() {
        val sessionTimeZone = jdbcTemplate.queryForObject("SELECT @@session.time_zone", String::class.java)
        if (sessionTimeZone != EXPECTED_SESSION_TIME_ZONE) {
            logger.warn(
                "MySQL session time_zone is '{}', expected '{}' — DB_URL is likely missing " +
                    "connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                sessionTimeZone,
                EXPECTED_SESSION_TIME_ZONE,
            )
        }
    }
}
