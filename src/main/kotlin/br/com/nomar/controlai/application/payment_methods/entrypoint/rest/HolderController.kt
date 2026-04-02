package br.com.nomar.controlai.application.payment_methods.entrypoint.rest

import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.request.CreateHolderRequest
import br.com.nomar.controlai.application.payment_methods.entrypoint.rest.response.HolderResponse
import br.com.nomar.controlai.domain.payment_methods.entity.Holder
import br.com.nomar.controlai.domain.payment_methods.usecase.ListHoldersUseCase
import br.com.nomar.controlai.domain.payment_methods.usecase.SaveHolderUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/holders")
class HolderController(
    private val saveHolderUseCase: SaveHolderUseCase,
    private val listHoldersUseCase: ListHoldersUseCase,
) {

    @GetMapping
    fun listHolders(): List<HolderResponse> =
        listHoldersUseCase.execute().getOrThrow().map(HolderResponse::from)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createHolder(@Validated @RequestBody request: CreateHolderRequest): HolderResponse {
        val holder = Holder(name = request.name)
        return HolderResponse.from(saveHolderUseCase.execute(holder).getOrThrow())
    }
}
