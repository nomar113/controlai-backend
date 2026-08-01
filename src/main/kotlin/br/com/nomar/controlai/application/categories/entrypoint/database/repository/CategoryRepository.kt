package br.com.nomar.controlai.application.categories.entrypoint.database.repository

import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import org.springframework.data.jpa.repository.JpaRepository

interface CategoryRepository : JpaRepository<CategoryModel, Long> {
    fun findAllByOrderByNameAsc(): List<CategoryModel>
    fun findAllByGroupIdOrderByNameAsc(groupId: Long): List<CategoryModel>
    fun findByIdAndGroupId(id: Long, groupId: Long): CategoryModel?
    fun countByGroupIdAndIdIn(groupId: Long, ids: Collection<Long>): Long
}
