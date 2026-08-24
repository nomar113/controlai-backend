package br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

// Accepts the standard ISO-8601 instant format (with 'Z' or an explicit offset). As a
// compatibility fallback for clients not yet updated to send an explicit offset, a naive
// local date-time string is also accepted and interpreted as America/Sao_Paulo — logging a
// WARN so those legacy callers can be detected and fixed after the deploy (PRD requisito 6).
class PurchasedAtDeserializer : JsonDeserializer<Instant>() {

    companion object {
        private val log = LoggerFactory.getLogger(PurchasedAtDeserializer::class.java)
        private val FALLBACK_ZONE = ZoneId.of("America/Sao_Paulo")
    }

    override fun deserialize(parser: JsonParser, context: DeserializationContext): Instant {
        val text = parser.text.trim()
        return try {
            Instant.parse(text)
        } catch (e: DateTimeParseException) {
            val fallback = LocalDateTime.parse(text).atZone(FALLBACK_ZONE).toInstant()
            log.warn(
                "purchasedAt received without explicit offset/zone (\"{}\"); assuming {} as fallback for outdated clients",
                text,
                FALLBACK_ZONE,
            )
            fallback
        }
    }
}
