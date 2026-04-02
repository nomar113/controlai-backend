package br.com.nomar.controlai.application.payment_methods.entrypoint.rest

import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request.CreatePaymentMethodRequest
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request.CreateSubCardRequest
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request.UpdatePaymentMethodRequest
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request.UpdateSubCardRequest
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response.PaymentMethodResponse
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response.PaymentMethodSummaryResponse
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response.SubCardResponse
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethod
import br.com.nomar.controlai.domain.payment_methods.entity.PaymentMethodType
import br.com.nomar.controlai.domain.payment_methods.entity.SubCard
import br.com.nomar.controlai.domain.payment_methods.entity.SubCardType
import br.com.nomar.controlai.domain.payment_methods.entity.WalletPlatform
import br.com.nomar.controlai.domain.payment_methods.usecase.*
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.YearMonth

@RestController
@RequestMapping("/payment-methods")
class PaymentMethodController(
    private val savePaymentMethodUseCase: SavePaymentMethodUseCase,
    private val listPaymentMethodsUseCase: ListPaymentMethodsUseCase,
    private val findPaymentMethodUseCase: FindPaymentMethodUseCase,
    private val updatePaymentMethodUseCase: UpdatePaymentMethodUseCase,
    private val deactivatePaymentMethodUseCase: DeactivatePaymentMethodUseCase,
    private val saveSubCardUseCase: SaveSubCardUseCase,
    private val updateSubCardUseCase: UpdateSubCardUseCase,
    private val deactivateSubCardUseCase: DeactivateSubCardUseCase,
    private val getPaymentMethodsSummaryUseCase: GetPaymentMethodsSummaryUseCase,
) {

    @GetMapping("/summary")
    fun getPaymentMethodsSummary(@RequestParam month: String): List<PaymentMethodSummaryResponse> {
        val yearMonth = try {
            YearMonth.parse(month)
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid month format. Expected YYYY-MM")
        }
        return getPaymentMethodsSummaryUseCase.execute(yearMonth)
            .map { summaries -> summaries.map(PaymentMethodSummaryResponse::from) }
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }
    }

    @GetMapping
    fun listPaymentMethods(@RequestParam holderId: Long? = null): List<PaymentMethodResponse> =
        listPaymentMethodsUseCase.execute(holderId).getOrThrow().map(PaymentMethodResponse::from)

    @GetMapping("/{id}")
    fun getPaymentMethod(@PathVariable id: Long): PaymentMethodResponse {
        return findPaymentMethodUseCase.execute(id)
            .map(PaymentMethodResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPaymentMethod(@Validated @RequestBody request: CreatePaymentMethodRequest): PaymentMethodResponse {
        val pm = PaymentMethod(
            name = request.name,
            type = PaymentMethodType.valueOf(request.type),
            holderId = request.holderId,
            brand = request.brand,
            closingDay = request.closingDay,
            subCards = request.subCards.map { toSubCard(0, it) },
        )
        return PaymentMethodResponse.from(savePaymentMethodUseCase.execute(pm).getOrThrow())
    }

    @PutMapping("/{id}")
    fun updatePaymentMethod(
        @PathVariable id: Long,
        @Validated @RequestBody request: UpdatePaymentMethodRequest,
    ): PaymentMethodResponse {
        val pm = PaymentMethod(
            id = id,
            name = request.name,
            type = PaymentMethodType.valueOf(request.type),
            holderId = request.holderId,
            brand = request.brand,
            closingDay = request.closingDay,
        )
        return updatePaymentMethodUseCase.execute(pm)
            .map(PaymentMethodResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePaymentMethod(@PathVariable id: Long) {
        deactivatePaymentMethodUseCase.execute(id)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    // --- Sub-cards ---

    @PostMapping("/{id}/sub-cards")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSubCard(
        @PathVariable id: Long,
        @Validated @RequestBody request: CreateSubCardRequest,
    ): SubCardResponse {
        val subCard = toSubCard(id, request)
        return SubCardResponse.from(saveSubCardUseCase.execute(subCard).getOrThrow())
    }

    @PutMapping("/{id}/sub-cards/{subCardId}")
    fun updateSubCard(
        @PathVariable id: Long,
        @PathVariable subCardId: Long,
        @Validated @RequestBody request: UpdateSubCardRequest,
    ): SubCardResponse {
        val subCard = SubCard(
            id = subCardId,
            paymentMethodId = id,
            lastFourDigits = request.lastFourDigits,
            type = SubCardType.valueOf(request.type),
            nickname = request.nickname,
            dependentName = request.dependentName,
            walletPlatform = request.walletPlatform?.let { WalletPlatform.valueOf(it) },
        )
        return updateSubCardUseCase.execute(subCard)
            .map(SubCardResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @DeleteMapping("/{id}/sub-cards/{subCardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteSubCard(@PathVariable id: Long, @PathVariable subCardId: Long) {
        deactivateSubCardUseCase.execute(subCardId)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    private fun toSubCard(paymentMethodId: Long, request: CreateSubCardRequest) = SubCard(
        paymentMethodId = paymentMethodId,
        lastFourDigits = request.lastFourDigits,
        type = SubCardType.valueOf(request.type),
        nickname = request.nickname,
        dependentName = request.dependentName,
        walletPlatform = request.walletPlatform?.let { WalletPlatform.valueOf(it) },
    )
}
