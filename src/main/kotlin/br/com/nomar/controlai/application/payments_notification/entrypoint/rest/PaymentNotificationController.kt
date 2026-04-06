package br.com.nomar.controlai.application.payments_notification.entrypoint.rest

import br.com.nomar.controlai.application.payments_notification.entrypoint.database.model.PaymentNotification
import br.com.nomar.controlai.application.payments_notification.entrypoint.database.repository.PaymentNotificationRepository
import br.com.nomar.controlai.application.payments_notification.entrypoint.queue.model.PaymentNotificationQueueMessage
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.ManualPaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.PaymentNotificationTextRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.request.UpdateDescriptionRequest
import br.com.nomar.controlai.application.payments_notification.entrypoint.rest.response.PaymentNotificationResponse
import br.com.nomar.controlai.domain.payments_notifications.usecase.NotifyPaymentNotificationQueueUseCase
import br.com.nomar.controlai.domain.payments_notifications.usecase.SavePaymentNotificationUseCase
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/payments")
class PaymentNotificationController(
    private val notificationQueueUseCase: NotifyPaymentNotificationQueueUseCase,
    private val savePaymentNotificationUseCase: SavePaymentNotificationUseCase,
    private val paymentNotificationRepository: PaymentNotificationRepository,
) {

    @GetMapping("/notifications")
    fun listNotifications(): List<PaymentNotificationResponse> =
        paymentNotificationRepository.findAll(Sort.by(Sort.Direction.DESC, "purchasedAt"))
            .map(PaymentNotificationResponse::from)

    @GetMapping("/notifications/{id}")
    fun getNotification(@PathVariable id: Long): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }
        return PaymentNotificationResponse.from(notification)
    }

    @PatchMapping("/notifications/{id}/description")
    fun updateDescription(
        @PathVariable id: Long,
        @RequestBody request: UpdateDescriptionRequest,
    ): PaymentNotificationResponse {
        val notification = paymentNotificationRepository.findById(id)
            .orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found") }
        val updated = paymentNotificationRepository.save(notification.copy(description = request.description))
        return PaymentNotificationResponse.from(updated)
    }

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

    @PostMapping("/notifications/manual")
    @ResponseStatus(HttpStatus.CREATED)
    fun createManualNotification(@Validated @RequestBody request: ManualPaymentNotificationRequest): PaymentNotificationResponse {
        val paymentNotification = PaymentNotification(
            cardLastDigits = request.cardLastDigits,
            purchasedAt = request.purchasedAt,
            amount = request.amount,
            merchantName = request.merchantName,
            numberOfInstallments = request.numberOfInstallments,
            origin = "MANUAL",
            originType = "MANUAL",
            category = request.category,
            paymentMethodId = request.paymentMethodId,
            subCardId = request.subCardId,
        )
        val saved = savePaymentNotificationUseCase.execute(paymentNotification).getOrThrow()
        return PaymentNotificationResponse.from(saved)
    }
}
