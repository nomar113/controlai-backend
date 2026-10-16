package br.com.nomar.controlai.application.auth.entrypoint.rest.request

import br.com.nomar.controlai.domain.auth.entity.AppleLoginPlatform
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size

data class LoginRequest(
    @field:NotBlank(message = "E-mail e obrigatorio")
    val email: String = "",

    @field:NotBlank(message = "Senha e obrigatoria")
    val password: String = "",
)

data class RefreshRequest(
    @field:NotBlank(message = "Refresh token e obrigatorio")
    val refreshToken: String = "",
)

data class LogoutRequest(
    @field:NotBlank(message = "Refresh token e obrigatorio")
    val refreshToken: String = "",
)

data class GoogleLoginRequest(
    @field:NotBlank(message = "ID token e obrigatorio")
    val idToken: String = "",
)

data class AppleLoginRequest(
    @field:NotBlank(message = "Identity token e obrigatorio")
    @field:Size(max = 8192, message = "Identity token invalido")
    val identityToken: String = "",

    @field:NotBlank(message = "Authorization code e obrigatorio")
    @field:Size(max = 512, message = "Authorization code invalido")
    val authorizationCode: String = "",

    @field:NotBlank(message = "Nonce e obrigatorio")
    @field:Size(max = 256, message = "Nonce invalido")
    val nonce: String = "",

    @field:Size(max = 255, message = "Nome muito longo")
    val fullName: String? = null,

    // Not read: the e-mail that counts is the one signed inside identityToken
    val email: String? = null,
)

data class ForgotPasswordRequest(
    @field:NotBlank(message = "E-mail e obrigatorio")
    @field:Email(message = "E-mail invalido")
    val email: String = "",
)

data class ResetPasswordRequest(
    @field:NotBlank(message = "Token e obrigatorio")
    val token: String = "",

    @field:NotBlank(message = "Nova senha e obrigatoria")
    @field:Size(min = 8, message = "A senha deve ter no minimo 8 caracteres")
    val newPassword: String = "",
)

data class ChangePasswordRequest(
    @field:NotBlank(message = "Senha atual e obrigatoria")
    val currentPassword: String = "",

    @field:NotBlank(message = "Nova senha e obrigatoria")
    @field:Size(min = 8, message = "A senha deve ter no minimo 8 caracteres")
    val newPassword: String = "",
)

data class AppleLoginStartRequest(
    @field:NotNull(message = "Plataforma e obrigatoria")
    val platform: AppleLoginPlatform? = null,
)

data class AppleSessionRequest(
    @field:NotBlank(message = "Codigo e obrigatorio")
    @field:Size(max = 128, message = "Codigo invalido")
    val code: String = "",
)
