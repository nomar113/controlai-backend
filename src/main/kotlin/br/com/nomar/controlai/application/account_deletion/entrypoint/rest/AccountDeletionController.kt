package br.com.nomar.controlai.application.account_deletion.entrypoint.rest

import br.com.nomar.controlai.domain.account_deletion.entity.AccountDeletion
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionAlreadyScheduledException
import br.com.nomar.controlai.domain.account_deletion.exception.AccountDeletionNotScheduledException
import br.com.nomar.controlai.domain.account_deletion.usecase.CancelAccountDeletionUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.GetAccountDeletionUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.RequestAccountDeletionUseCase
import br.com.nomar.controlai.domain.auth.RequestContext
import com.fasterxml.jackson.annotation.JsonInclude
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@RestController
@RequestMapping("/me/deletion")
@Tag(name = "Exclusao de conta", description = "Pedido, consulta e cancelamento da exclusao da conta do usuario logado")
class AccountDeletionController(
    private val requestAccountDeletionUseCase: RequestAccountDeletionUseCase,
    private val cancelAccountDeletionUseCase: CancelAccountDeletionUseCase,
    private val getAccountDeletionUseCase: GetAccountDeletionUseCase,
    private val requestContext: RequestContext,
) {

    enum class AccountDeletionStatus { NONE, SCHEDULED }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class AccountDeletionStatusResponse(
        val status: AccountDeletionStatus,
        val requestedAt: Instant? = null,
        val scheduledFor: Instant? = null,
    ) {
        companion object {
            fun from(deletion: AccountDeletion?) = when (deletion) {
                null -> AccountDeletionStatusResponse(AccountDeletionStatus.NONE)
                else -> AccountDeletionStatusResponse(
                    status = AccountDeletionStatus.SCHEDULED,
                    requestedAt = deletion.requestedAt,
                    scheduledFor = deletion.scheduledFor,
                )
            }
        }
    }

    data class AccountDeletionScheduledResponse(val scheduledFor: Instant)

    @GetMapping
    @Operation(summary = "Consulta se a exclusao da conta esta agendada")
    @ApiResponse(responseCode = "200", description = "NONE, ou SCHEDULED com as datas do pedido e da exclusao")
    fun get(): AccountDeletionStatusResponse =
        getAccountDeletionUseCase.execute(requestContext.userId)
            .map(AccountDeletionStatusResponse::from)
            .getOrElse { throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, it.message) }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Agenda a exclusao da conta para daqui a 30 dias",
        description = "Encerra todas as sessoes do usuario e cancela os convites pendentes enviados ou recebidos por ele",
    )
    @ApiResponse(responseCode = "202", description = "Exclusao agendada")
    @ApiResponse(responseCode = "409", description = "A exclusao ja esta agendada")
    fun request(): AccountDeletionScheduledResponse =
        requestAccountDeletionUseCase.execute(requestContext.userId)
            .map { AccountDeletionScheduledResponse(it.scheduledFor) }
            .getOrElse { ex ->
                when (ex) {
                    is AccountDeletionAlreadyScheduledException -> throw ResponseStatusException(HttpStatus.CONFLICT, ex.message)
                    else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
                }
            }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Cancela a exclusao agendada da conta")
    @ApiResponse(responseCode = "204", description = "Exclusao cancelada")
    @ApiResponse(responseCode = "404", description = "Nao ha exclusao agendada")
    fun cancel() {
        cancelAccountDeletionUseCase.execute(requestContext.userId).getOrElse { ex ->
            when (ex) {
                is AccountDeletionNotScheduledException -> throw ResponseStatusException(HttpStatus.NOT_FOUND, ex.message)
                else -> throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, ex.message)
            }
        }
    }
}
