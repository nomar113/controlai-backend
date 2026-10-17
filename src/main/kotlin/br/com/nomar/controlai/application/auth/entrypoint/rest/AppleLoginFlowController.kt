package br.com.nomar.controlai.application.auth.entrypoint.rest

import br.com.nomar.controlai.application.auth.application.AppleUserFieldParser
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.AppleLoginStartRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.AppleSessionRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.AppleLoginStartResponse
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.AuthResponse
import br.com.nomar.controlai.domain.auth.entity.AppleCallback
import br.com.nomar.controlai.domain.auth.usecase.CompleteAppleLoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.ExchangeAppleLoginCodeUseCase
import br.com.nomar.controlai.domain.auth.usecase.StartAppleLoginUseCase
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.net.URI

// Web and Android login: the app starts a flow, Apple posts back to /callback, and the app trades the
// handoff code for a session. iOS keeps using POST /auth/apple with the native sheet.
@RestController
@RequestMapping("/auth/apple")
class AppleLoginFlowController(
    private val startAppleLoginUseCase: StartAppleLoginUseCase,
    private val completeAppleLoginUseCase: CompleteAppleLoginUseCase,
    private val exchangeAppleLoginCodeUseCase: ExchangeAppleLoginCodeUseCase,
    private val appleUserFieldParser: AppleUserFieldParser,
) {

    @PostMapping("/start")
    fun start(@Validated @RequestBody request: AppleLoginStartRequest): AppleLoginStartResponse {
        val platform = requireNotNull(request.platform)
        return startAppleLoginUseCase.execute(platform)
            .map { AppleLoginStartResponse(nonce = it.nonce, state = it.state) }
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Nao foi possivel iniciar o login") }
    }

    // Apple posts form-encoded data here. Always answers with a redirect, including on failure or cancel.
    @PostMapping("/callback")
    fun callback(@RequestParam params: Map<String, String>): ResponseEntity<Void> {
        val callback = AppleCallback(
            authorizationCode = params["code"],
            identityToken = params["id_token"],
            fullName = appleUserFieldParser.parse(params["user"])?.fullName,
            state = params["state"],
        )
        val location = completeAppleLoginUseCase.execute(callback)
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(location)).build()
    }

    @PostMapping("/session")
    fun session(@Validated @RequestBody request: AppleSessionRequest): AuthResponse =
        exchangeAppleLoginCodeUseCase.execute(request.code)
            .map(AuthResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login com a Apple expirado ou ja utilizado") }
}
