package br.com.nomar.controlai.domain.payment_methods.usecase

import br.com.nomar.controlai.domain.payment_methods.entity.Holder
import br.com.nomar.controlai.domain.payment_methods.gateway.ListHoldersGateway
import org.springframework.stereotype.Component

@Component
class ListHoldersUseCase(
    private val listHoldersGateway: ListHoldersGateway,
) {
    fun execute(): Result<List<Holder>> {
        return runCatching {
            listHoldersGateway.execute().getOrThrow()
        }
    }
}
