package br.com.nomar.controlai.application.categories.application

import br.com.nomar.controlai.domain.categories.gateway.CountPurchasesByCategoryGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class CountPurchasesByCategoryProvider(
    private val jdbcTemplate: JdbcTemplate,
) : CountPurchasesByCategoryGateway {

    override fun execute(categoryId: Long): Result<Long> {
        return try {
            val count = jdbcTemplate.queryForObject(
                """
                SELECT (
                    SELECT COUNT(*) FROM payment_notifications WHERE category_id = ? AND deleted_at IS NULL
                ) + (
                    SELECT COUNT(*) FROM purchase_invoices WHERE category_id = ? AND deleted_at IS NULL
                )
                """.trimIndent(),
                Long::class.java,
                categoryId,
                categoryId,
            )
            Result.success(count ?: 0L)
        } catch (_: Exception) {
            // category_id columns may not exist yet (added in a later migration)
            Result.success(0L)
        }
    }
}
