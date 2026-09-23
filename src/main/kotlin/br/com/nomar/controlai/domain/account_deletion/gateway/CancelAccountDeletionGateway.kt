package br.com.nomar.controlai.domain.account_deletion.gateway

fun interface CancelAccountDeletionGateway {
    fun execute(userId: Long): Result<Unit>
}
