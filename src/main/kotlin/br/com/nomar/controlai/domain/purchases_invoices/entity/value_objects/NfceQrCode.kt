package br.com.nomar.controlai.domain.purchases_invoices.entity.value_objects

import java.net.URI

/**
 * Raw content of an NFC-e (model 65) QR Code, from any state.
 *
 * Supports QR Code v2/v3 (`p` parameter, access key as the first `|`-separated field)
 * and the legacy v1 (`chNFe` parameter). There is no fixed list of state domains:
 * the access key validation (model, cUF and modulo 11 check digit) is the guarantee.
 */
class NfceQrCode private constructor(
    val invoiceUrl: InvoiceUrl,
    val accessKey: AccessKey,
    // QR Code layout version (1, 2 or 3); null when the `p` parameter does not state it
    val version: Int?,
) {

    val uf: String get() = accessKey.value.take(UF_LENGTH)

    companion object {
        const val NOT_NFCE_MESSAGE = "Este QR Code não é de uma nota fiscal de consumidor (NFC-e)"

        private const val ACCESS_KEY_LENGTH = 44
        private const val NFCE_MODEL = "65"
        private const val UF_LENGTH = 2
        private const val LEGACY_VERSION = 1
        private val MODEL_RANGE = 20 until 22
        private val PIPE_SEPARATOR = Regex("\\||%7C", RegexOption.IGNORE_CASE)
        private val DIGITS = Regex("\\d+")

        // IBGE codes of the 27 federative units
        private val UF_CODES = setOf(
            "11", "12", "13", "14", "15", "16", "17",
            "21", "22", "23", "24", "25", "26", "27", "28", "29",
            "31", "32", "33", "35",
            "41", "42", "43",
            "50", "51", "52", "53",
        )

        // Throws IllegalArgumentException with a user-facing message when the content is not an NFC-e QR Code
        fun parse(rawContent: String): NfceQrCode {
            val query = extractQuery(rawContent)
            val (key, version) = extractAccessKey(query)
            validateAccessKey(key)
            val invoiceUrl = runCatching { InvoiceUrl.of(rawContent) }
                .getOrElse { throw IllegalArgumentException(NOT_NFCE_MESSAGE, it) }
            return NfceQrCode(invoiceUrl, AccessKey.of(key), version)
        }

        private fun extractQuery(rawContent: String): String {
            val queryStart = rawContent.indexOf('?')
            val base = if (queryStart >= 0) rawContent.take(queryStart) else rawContent
            val uri = runCatching { URI(base) }.getOrNull()
            require(uri != null && uri.scheme?.lowercase() in setOf("http", "https") && !uri.host.isNullOrBlank()) {
                NOT_NFCE_MESSAGE
            }
            require(queryStart >= 0) { NOT_NFCE_MESSAGE }

            val fragmentStart = rawContent.indexOf('#', queryStart + 1)
            return rawContent.substring(queryStart + 1, if (fragmentStart >= 0) fragmentStart else rawContent.length)
        }

        // Returns the access key and the QR Code version (2nd field of `p`, or 1 for the legacy `chNFe`)
        private fun extractAccessKey(query: String): Pair<String, Int?> {
            val params = query.split('&')
                .filter { it.contains('=') }
                .associate { it.substringBefore('=').trim().lowercase() to it.substringAfter('=') }

            val p = params["p"]
            if (!p.isNullOrBlank()) {
                val fields = p.split(PIPE_SEPARATOR)
                return fields.first().trim() to fields.getOrNull(1)?.trim()?.toIntOrNull()
            }

            val chNFe = params["chnfe"]
            require(!chNFe.isNullOrBlank()) { NOT_NFCE_MESSAGE }
            return chNFe.trim() to LEGACY_VERSION
        }

        private fun validateAccessKey(key: String) {
            require(key.length == ACCESS_KEY_LENGTH && DIGITS.matches(key)) { NOT_NFCE_MESSAGE }
            require(key.substring(MODEL_RANGE) == NFCE_MODEL) { NOT_NFCE_MESSAGE }
            require(key.take(UF_LENGTH) in UF_CODES) { NOT_NFCE_MESSAGE }
            require(checkDigit(key.take(ACCESS_KEY_LENGTH - 1)) == key.last().digitToInt()) { NOT_NFCE_MESSAGE }
        }

        // Modulo 11 with weights 2..9 applied from right to left
        private fun checkDigit(digits: String): Int {
            val sum = digits.reversed().mapIndexed { index, char -> char.digitToInt() * (2 + index % 8) }.sum()
            val remainder = sum % 11
            return if (remainder < 2) 0 else 11 - remainder
        }
    }
}
