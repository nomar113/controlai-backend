package br.com.nomar.controlai.application.categories

import br.com.nomar.controlai.application.categories.converter.CategoryConverter
import br.com.nomar.controlai.application.categories.entrypoint.database.model.CategoryModel
import br.com.nomar.controlai.domain.categories.entity.Category
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryConverterTest {

    private val converter = CategoryConverter()

    @Test
    fun `toEntity should convert model to entity`() {
        val model = CategoryModel(id = 5, name = "Mercado")
        val entity = converter.toEntity(model)

        assertEquals(5L, entity.id)
        assertEquals("Mercado", entity.name)
    }

    @Test
    fun `toModel should convert entity to model`() {
        val entity = Category(id = 3, name = "Transporte")
        val model = converter.toModel(entity)

        assertEquals(3L, model.id)
        assertEquals("Transporte", model.name)
    }

    @Test
    fun `toEntity with null id should preserve null`() {
        val model = CategoryModel(name = "Nova")
        val entity = converter.toEntity(model)

        assertEquals(null, entity.id)
        assertEquals("Nova", entity.name)
    }

    @Test
    fun `toModel with null id should preserve null`() {
        val entity = Category(name = "Nova")
        val model = converter.toModel(entity)

        assertEquals(null, model.id)
        assertEquals("Nova", model.name)
    }
}
