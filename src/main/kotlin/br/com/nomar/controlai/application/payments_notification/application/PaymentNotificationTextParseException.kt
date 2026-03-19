package br.com.nomar.controlai.application.payments_notification.application

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
class PaymentNotificationTextParseException(
    message: String = "Unable to parse payment notification text",
) : RuntimeException(message)
