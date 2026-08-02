package br.com.nomar.controlai.domain.groups.usecase

import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import br.com.nomar.controlai.domain.groups.exception.InviteNotFoundException
import br.com.nomar.controlai.domain.groups.exception.InviteNotPendingException
import br.com.nomar.controlai.domain.groups.gateway.FindInviteByIdGateway
import br.com.nomar.controlai.domain.groups.gateway.UpdateInviteStatusGateway
import org.springframework.stereotype.Component

@Component
class DeclineInviteUseCase(
    private val findInviteByIdGateway: FindInviteByIdGateway,
    private val updateInviteStatusGateway: UpdateInviteStatusGateway,
) {

    fun execute(inviteId: Long): Result<Unit> {
        return runCatching {
            val invite = findInviteByIdGateway.execute(inviteId).getOrThrow()
                ?: throw InviteNotFoundException()

            if (!invite.isPending()) throw InviteNotPendingException()

            updateInviteStatusGateway.execute(inviteId, InviteStatus.DECLINED).getOrThrow()
            Unit
        }
    }
}
