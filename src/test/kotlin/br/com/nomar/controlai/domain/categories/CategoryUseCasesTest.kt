package br.com.nomar.controlai.domain.categories

import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.gateway.*
import br.com.nomar.controlai.domain.categories.usecase.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryUseCasesTest {

    // --- SaveCategoryUseCase ---

    @Test
    fun `SaveCategoryUseCase should return saved category on success`() {
        val gateway = SaveCategoryGateway { Result.success(Category(id = 1, name = it.name)) }
        val useCase = SaveCategoryUseCase(gateway)

        val result = useCase.execute(Category(name = "Mercado"))

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull()?.id)
        assertEquals("Mercado", result.getOrNull()?.name)
    }

    @Test
    fun `SaveCategoryUseCase should return failure when gateway fails`() {
        val gateway = SaveCategoryGateway { Result.failure(IllegalStateException("duplicate name")) }
        val useCase = SaveCategoryUseCase(gateway)

        val result = useCase.execute(Category(name = "Mercado"))

        assertTrue(result.isFailure)
        assertEquals("duplicate name", result.exceptionOrNull()?.message)
    }

    // --- UpdateCategoryUseCase ---

    @Test
    fun `UpdateCategoryUseCase should return updated category`() {
        val gateway = UpdateCategoryGateway { Result.success(it) }
        val useCase = UpdateCategoryUseCase(gateway)

        val result = useCase.execute(Category(id = 1, name = "Alimentacao"))

        assertTrue(result.isSuccess)
        assertEquals("Alimentacao", result.getOrNull()?.name)
    }

    @Test
    fun `UpdateCategoryUseCase should return failure when gateway fails`() {
        val gateway = UpdateCategoryGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = UpdateCategoryUseCase(gateway)

        val result = useCase.execute(Category(id = 99, name = "X"))

        assertTrue(result.isFailure)
    }

    // --- ListCategoriesUseCase ---

    @Test
    fun `ListCategoriesUseCase should return list of categories`() {
        val categories = listOf(Category(id = 1, name = "Mercado"), Category(id = 2, name = "Pets"))
        val gateway = ListCategoriesGateway { Result.success(categories) }
        val useCase = ListCategoriesUseCase(gateway)

        val result = useCase.execute()

        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.size)
    }

    @Test
    fun `ListCategoriesUseCase should return failure when gateway fails`() {
        val gateway = ListCategoriesGateway { Result.failure(RuntimeException("db error")) }
        val useCase = ListCategoriesUseCase(gateway)

        val result = useCase.execute()

        assertTrue(result.isFailure)
    }

    // --- FindCategoryUseCase ---

    @Test
    fun `FindCategoryUseCase should return category by id`() {
        val category = Category(id = 1, name = "Moradia")
        val gateway = FindCategoryGateway { id ->
            if (id == 1L) Result.success(category) else Result.failure(NoSuchElementException("not found"))
        }
        val useCase = FindCategoryUseCase(gateway)

        val result = useCase.execute(1L)
        assertTrue(result.isSuccess)
        assertEquals("Moradia", result.getOrNull()?.name)

        val resultNotFound = useCase.execute(99L)
        assertTrue(resultNotFound.isFailure)
    }

    // --- DeleteCategoryUseCase ---

    @Test
    fun `DeleteCategoryUseCase should delete when no purchases linked`() {
        val countGateway = CountPurchasesByCategoryGateway { Result.success(0L) }
        val deleteGateway = DeleteCategoryGateway { Result.success(Unit) }
        val useCase = DeleteCategoryUseCase(countGateway, deleteGateway)

        val result = useCase.execute(1L)

        assertTrue(result.isSuccess)
    }

    @Test
    fun `DeleteCategoryUseCase should block deletion when purchases are linked`() {
        val countGateway = CountPurchasesByCategoryGateway { Result.success(5L) }
        val deleteGateway = DeleteCategoryGateway { Result.success(Unit) }
        val useCase = DeleteCategoryUseCase(countGateway, deleteGateway)

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("5 compra(s)") == true)
    }

    @Test
    fun `DeleteCategoryUseCase should return failure when count gateway fails`() {
        val countGateway = CountPurchasesByCategoryGateway { Result.failure(RuntimeException("db error")) }
        val deleteGateway = DeleteCategoryGateway { Result.success(Unit) }
        val useCase = DeleteCategoryUseCase(countGateway, deleteGateway)

        val result = useCase.execute(1L)

        assertTrue(result.isFailure)
        assertEquals("db error", result.exceptionOrNull()?.message)
    }

    @Test
    fun `DeleteCategoryUseCase should return failure when delete gateway fails`() {
        val countGateway = CountPurchasesByCategoryGateway { Result.success(0L) }
        val deleteGateway = DeleteCategoryGateway { Result.failure(NoSuchElementException("not found")) }
        val useCase = DeleteCategoryUseCase(countGateway, deleteGateway)

        val result = useCase.execute(99L)

        assertTrue(result.isFailure)
        assertEquals("not found", result.exceptionOrNull()?.message)
    }

    // --- Entity ---

    @Test
    fun `Category should be constructable with minimal fields`() {
        val category = Category(name = "Lazer")
        assertEquals("Lazer", category.name)
        assertEquals(null, category.id)
    }

    @Test
    fun `Category should be constructable with all fields`() {
        val category = Category(id = 10, name = "Viagens")
        assertEquals(10, category.id)
        assertEquals("Viagens", category.name)
    }
}
