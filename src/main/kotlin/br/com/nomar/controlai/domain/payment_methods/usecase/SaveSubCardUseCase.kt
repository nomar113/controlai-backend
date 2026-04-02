package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.SubCard
import br.com.nomar.controlai.domain.payment_methods.gateway.SaveSubCardGateway
import org.springframework.stereotype.Component

@Component
class SaveSubCardUseCase(
    private val saveSubCardGateway: SaveSubCardGateway,
) {
    fun execute(subCard: SubCard): Result<SubCard> {
        return runCatching {
            saveSubCardGateway.execute(subCard).getOrThrow()
        }
    }
}
