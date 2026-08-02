package br.com.nomar.controlai.domain.auth.exception

class EmailAlreadyUsedException(message: String = "E-mail ja cadastrado") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Credenciais invalidas") : RuntimeException(message)

class InvalidRefreshTokenException(message: String = "Sessao invalida ou expirada") : RuntimeException(message)

class InvalidGoogleTokenException(message: String = "Token Google invalido") : RuntimeException(message)

class InvalidResetTokenException(message: String = "Token de redefinicao invalido ou expirado") : RuntimeException(message)
