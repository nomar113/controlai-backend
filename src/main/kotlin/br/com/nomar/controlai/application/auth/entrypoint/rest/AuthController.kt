package br.com.nomar.controlai.application.auth.entrypoint.rest

import br.com.nomar.controlai.application.auth.entrypoint.rest.request.LoginRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.LogoutRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.RefreshRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.RegisterRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.AuthResponse
import br.com.nomar.controlai.domain.auth.exception.EmailAlreadyUsedException
import br.com.nomar.controlai.domain.auth.usecase.LoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.LogoutUseCase
import br.com.nomar.controlai.domain.auth.usecase.RefreshSessionUseCase
import br.com.nomar.controlai.domain.auth.usecase.RegisterUserUseCase
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/auth")
class AuthController(
    private val registerUserUseCase: RegisterUserUseCase,
    private val loginUseCase: LoginUseCase,
    private val refreshSessionUseCase: RefreshSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Validated @RequestBody request: RegisterRequest): AuthResponse =
        registerUserUseCase.execute(request.name, request.email, request.password)
            .map(AuthResponse::from)
            .getOrElse {
                when (it) {
                    is EmailAlreadyUsedException -> throw ResponseStatusException(HttpStatus.CONFLICT, it.message)
                    is IllegalArgumentException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, it.message)
                    else -> throw it
                }
            }

    // Generic 401 on any failure so the response never reveals whether the email exists
    @PostMapping("/login")
    fun login(@Validated @RequestBody request: LoginRequest): AuthResponse =
        loginUseCase.execute(request.email, request.password)
            .map(AuthResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciais invalidas") }

    @PostMapping("/refresh")
    fun refresh(@Validated @RequestBody request: RefreshRequest): AuthResponse =
        refreshSessionUseCase.execute(request.refreshToken)
            .map(AuthResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao invalida ou expirada") }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Validated @RequestBody request: LogoutRequest) {
        logoutUseCase.execute(request.refreshToken).getOrThrow()
    }
}
