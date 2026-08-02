package br.com.nomar.controlai.domain.groups.gateway

// Returns true if the group owns any user-created data (payment_methods, purchase_invoices, etc.)
// Used to gate force=true confirmation before discarding a personal group on invite accept.
fun interface GroupHasDataGateway {
    fun execute(groupId: Long): Result<Boolean>
}
