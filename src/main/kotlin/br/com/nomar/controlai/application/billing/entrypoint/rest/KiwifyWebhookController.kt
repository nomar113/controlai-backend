package br.com.nomar.controlai.application.billing.entrypoint.rest

import br.com.nomar.controlai.application.billing.entrypoint.rest.dto.KiwifyWebhookPayload
import br.com.nomar.controlai.domain.auth.TokenHasher
import br.com.nomar.controlai.domain.billing.usecase.HandleKiwifyWebhookUseCase
import br.com.nomar.controlai.domain.billing.usecase.ParsedKiwifyWebhookEvent
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.security.MessageDigest

@RestController
@RequestMapping("/webhooks/kiwify")
class KiwifyWebhookController(
    private val handleKiwifyWebhookUseCase: HandleKiwifyWebhookUseCase,
    objectMapper: ObjectMapper,
    @Value("\${kiwify.webhook-token}") private val webhookToken: String,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    // Local copy of the shared mapper: tolerant to unknown/renamed fields and casing, without
    // relaxing the app-wide ObjectMapper used elsewhere (Tech Spec: Riscos Conhecidos).
    private val tolerantMapper: ObjectMapper = objectMapper.copy()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)

    // permitAll + manual token validation, same pattern as ApiKeyAuthFilter for /payments/notification.
    @PostMapping
    fun receive(@RequestParam token: String?, @RequestBody rawPayload: String): ResponseEntity<Void> {
        if (!isValidToken(token)) {
            log.warn("Rejected Kiwify webhook: invalid or missing token")
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build()
        }

        val payload = parsePayload(rawPayload)
        val eventId = resolveEventId(payload, rawPayload)
        val orderStatus = resolveOrderStatus(payload)

        handleKiwifyWebhookUseCase.execute(
            ParsedKiwifyWebhookEvent(
                rawPayload = rawPayload,
                eventId = eventId,
                orderStatus = orderStatus,
                customerEmail = payload?.customer?.email,
                customerName = payload?.customer?.fullName,
                productId = payload?.product?.productId,
            ),
        ).onFailure { log.error("Failed to process Kiwify webhook event {}", eventId, it) }

        // Always 200, even on parsing/processing failure, so Kiwify does not retry aggressively;
        // the raw payload is persisted by the use case for manual reprocessing (Tech Spec: Pontos de Integração).
        return ResponseEntity.ok().build()
    }

    private fun isValidToken(token: String?): Boolean {
        if (webhookToken.isBlank() || token.isNullOrBlank()) return false
        return MessageDigest.isEqual(token.toByteArray(Charsets.UTF_8), webhookToken.toByteArray(Charsets.UTF_8))
    }

    private fun parsePayload(rawPayload: String): KiwifyWebhookPayload? =
        runCatching { tolerantMapper.readValue(rawPayload, KiwifyWebhookPayload::class.java) }
            .onFailure { log.error("Failed to parse Kiwify webhook payload", it) }
            .getOrNull()

    // Prefers an explicit event id from the payload; falls back to order id + status (a given
    // order only goes through each status once) and, as a last resort, a hash of the raw body.
    private fun resolveEventId(payload: KiwifyWebhookPayload?, rawPayload: String): String {
        payload?.webhookEventId?.takeIf { it.isNotBlank() }?.let { return it }
        val orderId = payload?.orderId?.takeIf { it.isNotBlank() }
        val orderStatus = payload?.orderStatus?.takeIf { it.isNotBlank() }
        if (orderId != null && orderStatus != null) return "$orderId:$orderStatus"
        return "unparsed:${TokenHasher.sha256(rawPayload)}"
    }

    // Sentinel values (instead of an empty string) keep the kiwify_webhook_events audit trail
    // informative when the body could not be parsed at all, or parsed without an order_status.
    private fun resolveOrderStatus(payload: KiwifyWebhookPayload?): String = when {
        payload == null -> "unparsed"
        payload.orderStatus.isNullOrBlank() -> "unknown-status"
        else -> payload.orderStatus
    }
}
