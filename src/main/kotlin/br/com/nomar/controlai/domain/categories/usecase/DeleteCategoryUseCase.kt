package br.com.nomar.controlai.domain.categories.usecase

import br.com.nomar.controlai.domain.categories.gateway.CountPurchasesByCategoryGateway
import br.com.nomar.controlai.domain.categories.gateway.DeleteCategoryGateway
import org.springframework.stereotype.Component

@Component
class DeleteCategoryUseCase(
    private val countPurchasesByCategoryGateway: CountPurchasesByCategoryGateway,
    private val deleteCategoryGateway: DeleteCategoryGateway,
) {
    fun execute(id: Long): Result<Unit> {
        return runCatching {
            val count = countPurchasesByCategoryGateway.execute(id).getOrThrow()
            if (count > 0) throw IllegalStateException(
                "Categoria vinculada a $count compra(s). Remova os vinculos antes de excluir."
            )
            deleteCategoryGateway.execute(id).getOrThrow()
        }
    }
}
