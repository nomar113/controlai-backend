package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.categories.gateway.DeleteCategoryGateway
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class DeleteCategoryProvider(
    private val categoryRepository: CategoryRepository,
) : DeleteCategoryGateway {

    override fun execute(id: Long): Result<Unit> {
        return runCatching {
            val model = categoryRepository.findById(id)
                .orElseThrow { NoSuchElementException("Categoria nao encontrada: $id") }
            val softDeleted = model.copy(deletedAt = LocalDateTime.now())
            categoryRepository.save(softDeleted)
        }
    }
}
