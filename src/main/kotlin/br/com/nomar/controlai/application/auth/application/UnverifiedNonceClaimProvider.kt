package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.domain.auth.gateway.ReadUnverifiedNonceClaimGateway
import com.nimbusds.jwt.JWTParser
import org.springframework.stereotype.Component

@Component
class UnverifiedNonceClaimProvider : ReadUnverifiedNonceClaimGateway {

    override fun execute(identityToken: String): Result<String?> {
        return runCatching { JWTParser.parse(identityToken).jwtClaimsSet.getStringClaim(CLAIM_NONCE) }
    }

    private companion object {
        const val CLAIM_NONCE = "nonce"
    }
}
