package br.com.nomar.controlai.domain.groups.gateway

// Moves a user from their current group to the target group.
// Caller is responsible for ensuring the user's old personal group is empty or force-cleared beforehand.
fun interface MoveUserToGroupGateway {
    fun execute(userId: Long, targetGroupId: Long): Result<Unit>
}
