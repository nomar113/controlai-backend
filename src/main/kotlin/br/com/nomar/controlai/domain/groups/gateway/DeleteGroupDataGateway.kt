package br.com.nomar.controlai.domain.groups.gateway

// Deletes all user-created data belonging to a group before it is abandoned.
// Only called when force=true is confirmed by the accepting user.
fun interface DeleteGroupDataGateway {
    fun execute(groupId: Long): Result<Unit>
}
