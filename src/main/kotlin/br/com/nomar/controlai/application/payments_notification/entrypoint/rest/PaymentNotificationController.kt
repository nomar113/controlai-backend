package br.com.nomar.controlai.application.payments_notification.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationTextRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
class PaymentNotificationController(
    private val notificationQueueUseCase: NotifyPaymentNotificationQueueUseCase,
    private val paymentNotificationRepository: PaymentNotificationRepository,
) {

    @GetMapping("/notifications")
    fun listNotifications(): List<PaymentNotificationResponse> =
        paymentNotificationRepository.findAll(Sort.by(Sort.Direction.DESC, "purchasedAt"))
            .map(PaymentNotificationResponse::from)

    @PostMapping("/notification")
    @ResponseStatus(HttpStatus.CREATED)
    fun enqueuePaymentNotification(@Validated @RequestBody request: PaymentNotificationTextRequest): Map<String, Any> {
        val queueMessage = PaymentNotificationQueueMessage(
            text = request.text,
            origin = request.origin,
            originType = request.originType ?: "HTTP_REQUEST",
        )
        notificationQueueUseCase.execute(queueMessage).getOrThrow()
        return mapOf(
            "text" to queueMessage.text.orEmpty(),
            "origin" to queueMessage.origin,
            "originType" to queueMessage.originType,
        )
    }
}
