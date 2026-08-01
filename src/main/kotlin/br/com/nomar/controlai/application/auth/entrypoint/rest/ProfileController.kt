package br.com.nomar.controlai.application.auth.entrypoint.rest

import br.com.nomar.controlai.application.auth.entrypoint.rest.response.ProfileResponse
import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.auth.usecase.GetProfileUseCase
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
class ProfileController(
    private val getProfileUseCase: GetProfileUseCase,
    private val requestContext: RequestContext,
) {

    @GetMapping("/me")
    fun me(): ProfileResponse =
        getProfileUseCase.execute(requestContext.userId)
            .map(ProfileResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
}
