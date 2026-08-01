package br.com.nomar.controlai.application.auth.application

import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import br.com.nomar.controlai.application.categories.entrypoint.database.repository.CategoryRepository
import br.com.nomar.controlai.domain.auth.gateway.SeedDefaultCategoriesGateway
import org.springframework.stereotype.Component

@Component
class SeedDefaultCategoriesProvider(
    private val categoryRepository: CategoryRepository,
) : SeedDefaultCategoriesGateway {

    override fun execute(groupId: Long) {
        val categories = DEFAULT_CATEGORIES.map { (name, icon) ->
            CategoryModel(groupId = groupId, name = name, icon = icon)
        }
        categoryRepository.saveAll(categories)
    }

    companion object {
        private val DEFAULT_CATEGORIES = listOf(
            "Alimentação" to "🍽️",
            "Assinaturas" to "📱",
            "Educação" to "📚",
            "Emergência" to "🚨",
            "Farmácia" to "💊",
            "Lazer" to "🎉",
            "Mercado" to "🛒",
            "Moradia" to "🏠",
            "Outros" to "📦",
            "Pet" to "🐾",
            "Restaurante" to "🍴",
            "Roupas" to "👕",
            "Saúde" to "❤️",
            "Tecnologia" to "💻",
            "Transporte" to "🚗",
        )
    }
}
