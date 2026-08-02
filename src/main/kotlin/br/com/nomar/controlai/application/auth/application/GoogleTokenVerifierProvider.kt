package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.entity.GoogleUserInfo
import br.com.nomar.controlai.domain.auth.exception.InvalidGoogleTokenException
import br.com.nomar.controlai.domain.auth.gateway.GoogleTokenVerifierGateway
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class GoogleTokenVerifierProvider(
    @Value("\${google.client-ids}") rawClientIds: String,
) : GoogleTokenVerifierGateway {

    private val verifier: GoogleIdTokenVerifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
    )
            .setAudience(rawClientIds.split(",").map(String::trim))
        .build()

    override fun verify(idToken: String): Result<GoogleUserInfo> {
        return runCatching {
            val parsed: GoogleIdToken = verifier.verify(idToken)
                ?: throw InvalidGoogleTokenException()
            val payload = parsed.payload
            val email = payload.email ?: throw InvalidGoogleTokenException("Token Google sem e-mail verificado")
            require(payload.emailVerified == true) { throw InvalidGoogleTokenException("E-mail Google nao verificado") }
            GoogleUserInfo(
                sub = payload.subject,
                email = email,
                name = (payload["name"] as? String) ?: email.substringBefore('@'),
            )
        }
    }
}
