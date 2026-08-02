package br.com.nomar.controlai.application.groups.application

import br.com.nomar.controlai.domain.groups.gateway.DeleteGroupDataGateway
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class DeleteGroupDataProvider(
    private val jdbcTemplate: JdbcTemplate,
) : DeleteGroupDataGateway {

    // Soft-deletes all user data belonging to a personal group that is being abandoned.
    // Categories are left as-is since they are just reference data.
    @Transactional
    override fun execute(groupId: Long): Result<Unit> {
        return runCatching {
            jdbcTemplate.update(
                "UPDATE payment_notifications SET deleted_at = NOW() WHERE purchase_invoice_id IN " +
                    "(SELECT id FROM purchase_invoices WHERE group_id = ?) AND deleted_at IS NULL",
                groupId,
            )
            jdbcTemplate.update(
                "UPDATE purchase_invoices SET deleted_at = NOW() WHERE group_id = ? AND deleted_at IS NULL",
                groupId,
            )
            jdbcTemplate.update(
                "UPDATE payment_methods SET deleted_at = NOW() WHERE group_id = ? AND deleted_at IS NULL",
                groupId,
            )
        }
    }
}
