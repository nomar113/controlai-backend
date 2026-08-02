package br.com.nomar.controlai.application.auth.entrypoint.rest.request

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "Nome e obrigatorio")
    val name: String = "",

    @field:NotBlank(message = "E-mail e obrigatorio")
    @field:Email(message = "E-mail invalido")
    val email: String = "",

    @field:NotBlank(message = "Senha e obrigatoria")
    @field:Size(min = 8, message = "A senha deve ter no minimo 8 caracteres")
    val password: String = "",
)

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
