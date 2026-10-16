package br.com.nomar.controlai.domain.auth.exception

class EmailAlreadyUsedException(message: String = "E-mail ja cadastrado") : RuntimeException(message)

class InvalidCredentialsException(message: String = "Credenciais invalidas") : RuntimeException(message)

class InvalidRefreshTokenException(message: String = "Sessao invalida ou expirada") : RuntimeException(message)

class InvalidGoogleTokenException(message: String = "Token Google invalido") : RuntimeException(message)

class InvalidAppleTokenException(message: String = "Token Apple invalido") : RuntimeException(message)

class AppleKeysUnavailableException(cause: Throwable) : RuntimeException("Chaves da Apple indisponiveis", cause)

class InvalidResetTokenException(message: String = "Token de redefinicao invalido ou expirado") : RuntimeException(message)

class InvalidAppleLoginFlowException(message: String = "Login com a Apple expirado ou ja utilizado") : RuntimeException(message)
