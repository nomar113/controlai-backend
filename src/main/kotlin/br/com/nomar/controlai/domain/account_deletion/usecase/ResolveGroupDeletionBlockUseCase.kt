package br.com.nomar.controlai.domain.account_deletion.usecase

import br.com.nomar.controlai.domain.account_deletion.gateway.FindDeletionSchedulesByGroupIdGateway
import org.springframework.stereotype.Component
import java.time.Instant

// Tells whether requests made with a group's API key must be blocked during the deletion grace
// period. A non-null result is the deletion date to report to the client.
@Component
class ResolveGroupDeletionBlockUseCase(
    private val findDeletionSchedulesByGroupIdGateway: FindDeletionSchedulesByGroupIdGateway,
) {

    // A group is only blocked when every member asked for deletion: a partner who stays keeps
    // the group's API key (SMS shortcut) working. The group's data goes away with the last
    // member to be purged, hence the latest date.
    fun execute(groupId: Long): Result<Instant?> =
        findDeletionSchedulesByGroupIdGateway.execute(groupId).map { schedules ->
            val scheduled = schedules.filterNotNull()
            if (scheduled.isNotEmpty() && scheduled.size == schedules.size) scheduled.max() else null
        }
}
