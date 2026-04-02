package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.SubCard
import br.com.nomar.controlai.domain.payment_methods.gateway.UpdateSubCardGateway
import org.springframework.stereotype.Component

@Component
class UpdateSubCardUseCase(
    private val updateSubCardGateway: UpdateSubCardGateway,
) {
    fun execute(subCard: SubCard): Result<SubCard> {
        return runCatching {
            updateSubCardGateway.execute(subCard).getOrThrow()
        }
    }
}
