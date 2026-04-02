package br.com.nomar.controlai.application.payment_methods.application

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository.SubCardRepository
import br.com.nomar.controlai.domain.payment_methods.gateway.DeactivateSubCardGateway
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Component
class DeactivateSubCardProvider(
    private val subCardRepository: SubCardRepository,
) : DeactivateSubCardGateway {

    @Transactional
    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = subCardRepository.findById(id)
                .orElseThrow { NoSuchElementException("SubCard not found: $id") }

            subCardRepository.save(model.copy(deletedAt = LocalDateTime.now()))
        }
    }
}
