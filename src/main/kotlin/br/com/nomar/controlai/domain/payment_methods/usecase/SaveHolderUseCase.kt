package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.Holder
import br.com.nomar.controlai.domain.payment_methods.gateway.SaveHolderGateway
import org.springframework.stereotype.Component

@Component
class SaveHolderUseCase(
    private val saveHolderGateway: SaveHolderGateway,
) {
    fun execute(holder: Holder): Result<Holder> {
        return runCatching {
            saveHolderGateway.execute(holder).getOrThrow()
        }
    }
}
