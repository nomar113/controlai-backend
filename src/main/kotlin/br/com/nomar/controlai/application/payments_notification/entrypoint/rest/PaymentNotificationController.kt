package br.com.nomar.controlai.application.payments_notification.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.application.PaymentNotificationTextParser
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationTextRequest
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentNotificationController(
    private val notificationQueueUseCase: NotifyPaymentNotificationQueueUseCase,
    private val paymentNotificationTextParser: PaymentNotificationTextParser,
) {

    @PostMapping("/notification")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePaymentNotification(@Validated @RequestBody request: PaymentNotificationTextRequest): Map<String, Any> {
        val paymentNotification = paymentNotificationTextParser.parse(
            text = request.text,
            origin = request.origin,
            originType = request.originType ?: "HTTP_REQUEST",
        )
        notificationQueueUseCase.execute(paymentNotification).getOrThrow()
        return mapOf(
            "cardLastDigits" to paymentNotification.cardLastDigits,
            "purchasedAt" to paymentNotification.purchasedAt,
            "amount" to paymentNotification.amount,
            "merchantName" to paymentNotification.merchantName,
            "numberOfInstallments" to paymentNotification.numberOfInstallments,
            "origin" to paymentNotification.origin,
            "originType" to paymentNotification.originType,
        )
    }

    @PostMapping("/from-notification")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendPaymentNotification(@Validated @RequestBody request: PaymentNotificationRequest): PaymentNotification {
        val paymentNotification = PaymentNotification(
            cardLastDigits = request.cardLastDigits,
            purchasedAt = request.purchasedAt,
            amount = request.amount,
            merchantName = request.merchantName,
            origin = "HTTP_REQUEST",
            originType = "HTTP_REQUEST",
        )

        notificationQueueUseCase.execute(paymentNotification)
        return paymentNotification
    }
}
