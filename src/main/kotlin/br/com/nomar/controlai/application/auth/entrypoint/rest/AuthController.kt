package br.com.nomar.controlai.application.auth.entrypoint.rest

import br.com.nomar.controlai.application.auth.entrypoint.rest.request.ForgotPasswordRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.GoogleLoginRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.LoginRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.LogoutRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.RefreshRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.request.ResetPasswordRequest
import br.com.nomar.controlai.application.auth.entrypoint.rest.response.AuthResponse
import br.com.nomar.controlai.domain.auth.exception.InvalidResetTokenException
import br.com.nomar.controlai.domain.auth.usecase.ForgotPasswordUseCase
import br.com.nomar.controlai.domain.auth.usecase.GoogleLoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.LoginUseCase
import br.com.nomar.controlai.domain.auth.usecase.LogoutUseCase
import br.com.nomar.controlai.domain.auth.usecase.RefreshSessionUseCase
import br.com.nomar.controlai.domain.auth.usecase.ResetPasswordUseCase
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
    private val loginUseCase: LoginUseCase,
    private val googleLoginUseCase: GoogleLoginUseCase,
    private val refreshSessionUseCase: RefreshSessionUseCase,
    private val logoutUseCase: LogoutUseCase,
    private val forgotPasswordUseCase: ForgotPasswordUseCase,
    private val resetPasswordUseCase: ResetPasswordUseCase,
) {

    // Public registration was retired: every new account now comes from an approved Kiwify
    // purchase (HandleKiwifyWebhookUseCase) or already existed before the commercial launch
    // (grandfathered). Kept mapped (instead of deleted) so old app builds get an explicit,
    // explanatory response instead of a bare 404.
    @PostMapping("/register")
    fun register(): Nothing =
        throw ResponseStatusException(
            HttpStatus.GONE,
            "Cadastro publico foi descontinuado. Sua conta no ControlAI agora e criada automaticamente ao assinar o plano.",
        )

    @PostMapping("/google")
    fun googleLogin(@Validated @RequestBody request: GoogleLoginRequest): AuthResponse =
        googleLoginUseCase.execute(request.idToken)
            .map(AuthResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token Google invalido ou expirado") }

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

    // Always responds 204 regardless of whether the email exists (RF-2.3)
    @PostMapping("/password/forgot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun forgotPassword(@Validated @RequestBody request: ForgotPasswordRequest) {
        forgotPasswordUseCase.execute(request.email).getOrThrow()
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun resetPassword(@Validated @RequestBody request: ResetPasswordRequest) {
        resetPasswordUseCase.execute(request.token, request.newPassword)
            .getOrElse { ex ->
                when (ex) {
                    is InvalidResetTokenException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
                    is IllegalArgumentException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
                    else -> throw ex
                }
            }
    }
}
