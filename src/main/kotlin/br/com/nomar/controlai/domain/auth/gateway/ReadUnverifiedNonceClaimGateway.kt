package br.com.nomar.controlai.domain.auth.gateway

// Reads the nonce claim without checking the signature. Only used to find the login flow; the
// identity token is fully verified afterwards, and that verification compares the claim with the flow's nonce.
fun interface ReadUnverifiedNonceClaimGateway {
    fun execute(identityToken: String): Result<String?>
}
