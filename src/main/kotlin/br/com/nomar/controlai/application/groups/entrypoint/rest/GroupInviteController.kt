package br.com.nomar.controlai.application.groups.entrypoint.rest

import br.com.nomar.controlai.domain.auth.RequestContext
import br.com.nomar.controlai.domain.groups.exception.CannotLeavePersonalGroupException
import br.com.nomar.controlai.domain.groups.exception.InviteNotFoundException
import br.com.nomar.controlai.domain.groups.exception.InviteNotPendingException
import br.com.nomar.controlai.domain.groups.exception.InviteeAlreadyInGroupException
import br.com.nomar.controlai.domain.groups.exception.PersonalGroupHasDataException
import br.com.nomar.controlai.domain.groups.usecase.AcceptInviteUseCase
import br.com.nomar.controlai.domain.groups.usecase.DeclineInviteUseCase
import br.com.nomar.controlai.domain.groups.usecase.InviteToGroupUseCase
import br.com.nomar.controlai.domain.groups.usecase.LeaveGroupUseCase
import br.com.nomar.controlai.domain.groups.usecase.ListPendingInvitesUseCase
import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDateTime

@RestController
class GroupInviteController(
    private val inviteToGroupUseCase: InviteToGroupUseCase,
    private val acceptInviteUseCase: AcceptInviteUseCase,
    private val declineInviteUseCase: DeclineInviteUseCase,
    private val leaveGroupUseCase: LeaveGroupUseCase,
    private val listPendingInvitesUseCase: ListPendingInvitesUseCase,
    private val requestContext: RequestContext,
) {

    data class InviteRequest(
        @field:NotBlank(message = "E-mail e obrigatorio")
        @field:Email(message = "E-mail invalido")
        val email: String = "",
    )

    data class InviteResponse(
        val id: Long,
        val groupId: Long,
        val inviteeEmail: String,
        val token: String,
        val expiresAt: LocalDateTime,
        val createdAt: LocalDateTime?,
    )

    data class PendingInviteResponse(
        val id: Long,
        val groupId: Long,
        val inviterUserId: Long,
        val token: String,
        val expiresAt: LocalDateTime,
    )

    @PostMapping("/invites")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendInvite(@Valid @RequestBody request: InviteRequest): InviteResponse {
        return inviteToGroupUseCase
            .execute(requestContext.userId, requestContext.groupId, request.email)
            .map { invite ->
                InviteResponse(
                    id = invite.id!!,
                    groupId = invite.groupId,
                    inviteeEmail = invite.inviteeEmail,
                    token = invite.token,
                    expiresAt = invite.expiresAt,
                    createdAt = invite.createdAt,
                )
            }
            .getOrElse { ex ->
                when (ex) {
                    is InviteeAlreadyInGroupException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    is IllegalArgumentException -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
    }

    @GetMapping("/invites/pending")
    fun getPendingInvites(): List<PendingInviteResponse> {
        return listPendingInvitesUseCase.execute(requestContext.email)
            .map { invites ->
                invites.map { invite ->
                    PendingInviteResponse(
                        id = invite.id!!,
                        groupId = invite.groupId,
                        inviterUserId = invite.inviterUserId,
                        token = invite.token,
                        expiresAt = invite.expiresAt,
                    )
                }
            }
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }
    }

    @PostMapping("/invites/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptInvite(
        @PathVariable id: Long,
        @RequestParam(defaultValue = "false") force: Boolean,
    ) {
        acceptInviteUseCase.execute(id, requestContext.userId, force)
            .getOrElse { ex ->
                when (ex) {
                    is InviteNotFoundException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                    is InviteNotPendingException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    is PersonalGroupHasDataException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
    }

    @PostMapping("/invites/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun declineInvite(@PathVariable id: Long) {
        declineInviteUseCase.execute(id)
            .getOrElse { ex ->
                when (ex) {
                    is InviteNotFoundException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                    is InviteNotPendingException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
    }

    @PostMapping("/groups/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveGroup() {
        leaveGroupUseCase.execute(requestContext.userId)
            .getOrElse { ex ->
                when (ex) {
                    is CannotLeavePersonalGroupException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    is NoSuchElementException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }
    }
}
