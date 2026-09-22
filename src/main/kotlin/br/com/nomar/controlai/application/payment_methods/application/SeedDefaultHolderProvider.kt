package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.HolderModel
import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.HolderRepository
import br.com.nomar.controlai.domain.payment_methods.gateway.SeedDefaultHolderGateway
import org.springframework.stereotype.Component

// Every payment method requires a holder, so a group without one cannot register cards.
@Component
class SeedDefaultHolderProvider(
    private val holderRepository: HolderRepository,
) : SeedDefaultHolderGateway {

    override fun execute(groupId: Long, holderName: String) {
        val name = holderName.trim().ifBlank { DEFAULT_HOLDER_NAME }.take(MAX_NAME_LENGTH)
        holderRepository.save(HolderModel(groupId = groupId, name = name))
    }

    companion object {
        private const val DEFAULT_HOLDER_NAME = "Titular"
        private const val MAX_NAME_LENGTH = 100
    }
}
