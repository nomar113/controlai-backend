package br.com.nomar.controlai.application.purchase.entrypoint.rest

import br.com.nomar.controlai.application.purchase.entrypoint.database.model.Purchase
import br.com.nomar.controlai.application.purchase.entrypoint.database.repository.PurchaseRepository
import br.com.nomar.controlai.application.purchase.entrypoint.rest.request.PurchaseRequest
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/purchases")
class PurchaseController(
    private val purchaseRepository: PurchaseRepository
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPurchase(@Validated @RequestBody request: PurchaseRequest): Purchase {
        val purchase = Purchase(
            cardLastDigits = request.cardLastDigits,
            purchasedAt = request.purchasedAt,
            amount = request.amount,
            merchantName = request.merchantName
        )

        return purchaseRepository.save(purchase)
    }
}