package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.domain.groups.gateway.GroupHasDataGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class GroupHasDataProvider(
    private val jdbcTemplate: JdbcTemplate,
) : GroupHasDataGateway {

    // A group "has data" if it owns any payment_methods or purchase_invoices.
    // Categories are seeded by default and are not considered user data.
    override fun execute(groupId: Long): Result<Boolean> {
        return runCatching {
            val paymentMethodCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payment_methods WHERE group_id = ? AND deleted_at IS NULL",
                Int::class.java,
                groupId,
            ) ?: 0
            if (paymentMethodCount > 0) return@runCatching true

            val invoiceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM purchase_invoices WHERE group_id = ? AND deleted_at IS NULL",
                Int::class.java,
                groupId,
            ) ?: 0
            invoiceCount > 0
        }
    }
}
