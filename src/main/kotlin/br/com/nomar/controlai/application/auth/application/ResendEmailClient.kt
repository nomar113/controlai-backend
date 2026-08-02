package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

@Component
class ResendEmailClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${resend.api-key}") private val apiKey: String,
    @Value("\${resend.from-email}") private val fromEmail: String,
) : EmailGateway {

    private val restClient = restClientBuilder.build()

    override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String): Result<Unit> {
        return runCatching {
            val html = buildPasswordResetHtml(toName, resetLink)
            val body = mapOf(
                "from" to fromEmail,
                "to" to listOf(toEmail),
                "subject" to "Redefinição de senha — ControlAI",
                "html" to html,
            )

            restClient.post()
                .uri(RESEND_API_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer $apiKey")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity()

            logger.info("Password reset email sent to user {}", toEmail.take(3) + "***")
        }
    }

    private fun buildPasswordResetHtml(name: String, link: String): String {
        return """
            <!DOCTYPE html>
            <html lang="pt-BR">
            <head><meta charset="UTF-8"><title>Redefinição de Senha</title></head>
            <body style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px; color: #333;">
              <h2 style="color: #1a1a2e;">Redefinição de Senha — ControlAI</h2>
              <p>Olá, ${name.split(" ").first()}!</p>
              <p>Recebemos uma solicitação para redefinir a senha da sua conta no ControlAI.</p>
              <p>Clique no botão abaixo para criar uma nova senha. Este link é válido por <strong>1 hora</strong>.</p>
              <div style="text-align: center; margin: 32px 0;">
                <a href="$link"
                   style="background-color: #1a1a2e; color: #ffffff; padding: 14px 28px;
                          text-decoration: none; border-radius: 8px; font-size: 16px; font-weight: bold;">
                  Redefinir Senha
                </a>
              </div>
              <p>Se você não solicitou a redefinição de senha, ignore este e-mail. Sua senha permanecerá a mesma.</p>
              <hr style="border: none; border-top: 1px solid #e5e7eb; margin: 24px 0;">
              <p style="font-size: 12px; color: #6b7280;">
                Se o botão não funcionar, copie e cole este link no seu navegador:<br>
                <a href="$link" style="color: #1a1a2e;">$link</a>
              </p>
            </body>
            </html>
        """.trimIndent()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ResendEmailClient::class.java)
        private const val RESEND_API_URL = "https://api.resend.com/emails"
    }
}
