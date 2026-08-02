package br.com.nomar.controlai.domain.groups.gateway

// Creates a new personal group for a user who is leaving a shared group,
// seeds default categories, and sets it as their active group.
fun interface CreatePersonalGroupGateway {
    fun execute(userId: Long, groupName: String): Result<Long>
}
