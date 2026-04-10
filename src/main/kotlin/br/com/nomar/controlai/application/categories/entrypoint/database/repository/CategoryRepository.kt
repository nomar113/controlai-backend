package br.com.nomar.controlai.application.categories.entrypoint.database.repository

import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<CategoryModel, Long> {
    fun findAllByOrderByNameAsc(): List<CategoryModel>
}
