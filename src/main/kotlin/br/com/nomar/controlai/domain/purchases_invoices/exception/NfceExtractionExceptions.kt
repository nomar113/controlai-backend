package br.com.nomar.controlai.domain.purchases_invoices.exception

class NfceExtractionBlockedException(
    message: String = "SEFAZ bloqueou a consulta a nota",
) : RuntimeException(message)

class NfceExtractionTimeoutException(
    message: String = "Tempo esgotado ao consultar a SEFAZ",
) : RuntimeException(message)

class NfceExtractionNavigationException(
    message: String = "Não foi possível consultar a SEFAZ",
    cause: Throwable? = null,
) : RuntimeException(message, cause)
