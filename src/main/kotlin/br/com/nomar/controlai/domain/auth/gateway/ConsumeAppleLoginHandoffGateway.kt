package br.com.nomar.controlai.domain.auth.gateway

// Returns false when the handoff code was already exchanged
fun interface ConsumeAppleLoginHandoffGateway {
    fun execute(flowId: Long): Result<Boolean>
}
