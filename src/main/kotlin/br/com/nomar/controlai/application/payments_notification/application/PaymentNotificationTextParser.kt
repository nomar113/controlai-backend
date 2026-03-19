package br.com.nomar.controlai.application.payments_notification.application

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Component
class PaymentNotificationTextParser {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val installmentsPattern = Regex("""\bEM\s+(\d+)\s*X\b""", RegexOption.IGNORE_CASE)
    private val notificationPattern = Regex(
        """FINAL\s+(\d{4})\s+EM\s+(\d{2}/\d{2}/\d{4})\s+(\d{2}:\d{2})(?:\.\s+|\s+)(?:NO\s+)?VALOR\s+DE\s+R\${'$'}\s*([\d\.]+,\d{2})(?:\s+EM\s+\d+\s*X)?\s+(.+?)\.?${'$'}""",
        RegexOption.IGNORE_CASE
    )

    fun parse(text: String, origin: String, originType: String): PaymentNotification {
        val match = notificationPattern.find(text)
            ?: throw PaymentNotificationTextParseException()

        val cardLastDigits = match.groupValues[1]
        val purchasedAt = LocalDateTime.parse("${match.groupValues[2]} ${match.groupValues[3]}", dateTimeFormatter)
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
}
