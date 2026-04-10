package br.com.nomar.controlai.application.categories.entrypoint.rest

import br.com.nomar.controlai.application.categories.entrypoint.rest.request.CreateCategoryRequest
import br.com.nomar.controlai.application.categories.entrypoint.rest.request.UpdateCategoryRequest
import br.com.nomar.controlai.application.categories.entrypoint.rest.response.CategoryResponse
import br.com.nomar.controlai.domain.categories.entity.Category
import br.com.nomar.controlai.domain.categories.usecase.*
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/categories")
class CategoryController(
    private val saveCategoryUseCase: SaveCategoryUseCase,
    private val listCategoriesUseCase: ListCategoriesUseCase,
    private val findCategoryUseCase: FindCategoryUseCase,
    private val updateCategoryUseCase: UpdateCategoryUseCase,
    private val deleteCategoryUseCase: DeleteCategoryUseCase,
) {

    @GetMapping
    fun listCategories(): List<CategoryResponse> =
        listCategoriesUseCase.execute().getOrThrow().map(CategoryResponse::from)

    @GetMapping("/{id}")
    fun getCategory(@PathVariable id: Long): CategoryResponse {
        return findCategoryUseCase.execute(id)
            .map(CategoryResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message) }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createCategory(@Validated @RequestBody request: CreateCategoryRequest): CategoryResponse {
        val category = Category(name = request.name)
        return saveCategoryUseCase.execute(category)
            .map(CategoryResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.CONFLICT, "Categoria com este nome ja existe.") }
    }

    @PutMapping("/{id}")
    fun updateCategory(
        @PathVariable id: Long,
        @Validated @RequestBody request: UpdateCategoryRequest,
    ): CategoryResponse {
        val category = Category(id = id, name = request.name)
        return updateCategoryUseCase.execute(category)
            .map(CategoryResponse::from)
            .getOrElse {
                when (it) {
                    is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
                    else -> throw ResponseStatusException(HttpStatus.CONFLICT, "Categoria com este nome ja existe.")
                }
            }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteCategory(@PathVariable id: Long) {
        deleteCategoryUseCase.execute(id)
            .getOrElse {
                when (it) {
                    is IllegalStateException -> throw ResponseStatusException(HttpStatus.CONFLICT, it.message)
                    else -> throw ResponseStatusException(HttpStatus.NOT_FOUND, it.message)
                }
            }
    }
}
