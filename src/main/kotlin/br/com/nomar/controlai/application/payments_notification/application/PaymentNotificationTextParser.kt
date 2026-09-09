package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Component
class PaymentNotificationTextParser {
    private val log = LoggerFactory.getLogger(javaClass)
    private val saoPauloZone = ZoneId.of("America/Sao_Paulo")
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val installmentsPattern = Regex("""\bEM\s+(\d+)\s*X\b""", RegexOption.IGNORE_CASE)
    private val notificationPattern = Regex(
        """FINAL\s+(\d{4})\s+EM\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2})(?:\.\s+|\s+)(?:NO\s+)?VALOR\s+DE\s+R\${'$'}\s*([\d\.]+,\d{2})(?:\s+EM\s+\d+\s*X)?\s+(.+?)\.?${'$'}""",
        RegexOption.IGNORE_CASE
    )

    // Itau's SMS puts the amount and merchant before the date, omits the year, and
    // uses "as" instead of a formal separator: "Compra aprovada de R$ 43,12 em LOJA,
    // 08/09 as 20:24 no seu Cartao Itau final 8415."
    private val itauNotificationPattern = Regex(
        """COMPRA\s+APROVADA\s+DE\s+R\${'$'}\s*([\d\.]+,\d{2})\s+EM\s+(.+?),\s+(\d{2}/\d{2})\s+AS\s+(\d{2}:\d{2})\s+NO\s+SEU\s+.+?\s+FINAL\s+(\d{4})\.?${'$'}""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, origin: String, originType: String): PaymentNotification {
        return if (origin == ITAU_ORIGIN) {
            parseItau(text, origin, originType)
        } else {
            parseBradesco(text, origin, originType)
        }
    }

    private fun parseBradesco(text: String, origin: String, originType: String): PaymentNotification {
        val match = notificationPattern.find(text)
            ?: run {
                log.warn("Payment notification text did not match expected date/time format. origin={}, originType={}", origin, originType)
                throw PaymentNotificationTextParseException()
            }

        val cardLastDigits = match.groupValues[1]
        // The bank SMS timestamp is always Brasilia time (America/Sao_Paulo),
        // regardless of the host timezone the backend runs on.
        val purchasedAt = LocalDateTime
            .parse("${match.groupValues[2]} ${match.groupValues[3]}", dateTimeFormatter)
            .atZone(saoPauloZone)
            .toInstant()
        val amount = parseAmount(match.groupValues[4])
        val merchantName = normalizeMerchantName(match.groupValues[5])
        val numberOfInstallments = parseInstallments(text)

        return PaymentNotification(
            cardLastDigits = cardLastDigits,
            purchasedAt = purchasedAt,
            amount = amount,
            merchantName = merchantName,
            numberOfInstallments = numberOfInstallments,
            origin = origin,
            originType = normalizeOriginType(originType),
        )
    }

    private fun parseItau(text: String, origin: String, originType: String): PaymentNotification {
        val match = itauNotificationPattern.find(text)
            ?: run {
                log.warn("Payment notification text did not match expected Itau format. origin={}, originType={}", origin, originType)
                throw PaymentNotificationTextParseException()
            }

        val amount = parseAmount(match.groupValues[1])
        val merchantName = normalizeMerchantName(match.groupValues[2])
        val dayMonth = match.groupValues[3]
        val time = match.groupValues[4]
        val cardLastDigits = match.groupValues[5]

        // The SMS omits the year, so it's inferred from the current Brasilia date. If that
        // yields a date more than a day in the future, the message is from late December
        // and was processed after the new year rolled over, so roll the year back.
        val now = ZonedDateTime.now(saoPauloZone)
        val withCurrentYear = LocalDateTime
            .parse("$dayMonth/${now.year} $time", dateTimeFormatter)
            .atZone(saoPauloZone)
        val purchasedAtZoned = if (withCurrentYear.isAfter(now.plusDays(1))) {
            withCurrentYear.minusYears(1)
        } else {
            withCurrentYear
        }

        return PaymentNotification(
            cardLastDigits = cardLastDigits,
            purchasedAt = purchasedAtZoned.toInstant(),
            amount = amount,
            merchantName = merchantName,
            numberOfInstallments = parseInstallments(text),
            origin = origin,
            originType = normalizeOriginType(originType),
        )
    }

    private fun parseAmount(value: String): BigDecimal {
        val normalized = value
            .replace(".", "")
            .replace(",", ".")
        return BigDecimal(normalized)
    }

    private fun normalizeMerchantName(value: String): String {
        return value
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
    }

    private fun parseInstallments(text: String): Int {
        return installmentsPattern.find(text)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 1
    }

    private fun normalizeOriginType(originType: String): String {
        val normalized = originType.trim().uppercase()
        return if (normalized == "SMS" || normalized == "HTTP_REQUEST") normalized else "HTTP_REQUEST"
    }

    companion object {
        private const val ITAU_ORIGIN = "ITAU_CARTOES"
    }
}
