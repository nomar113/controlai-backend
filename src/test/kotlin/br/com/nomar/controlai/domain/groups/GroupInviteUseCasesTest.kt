package br.com.nomar.controlai.domain.groups

import br.com.nomar.controlai.domain.auth.entity.User
import br.com.nomar.controlai.domain.auth.gateway.EmailGateway
import br.com.nomar.controlai.domain.groups.entity.GroupInvite
import br.com.nomar.controlai.domain.groups.entity.InviteStatus
import br.com.nomar.controlai.domain.groups.exception.CannotLeavePersonalGroupException
import br.com.nomar.controlai.domain.groups.exception.InviteNotFoundException
import br.com.nomar.controlai.domain.groups.exception.InviteNotPendingException
import br.com.nomar.controlai.domain.groups.exception.InviteeAlreadyInGroupException
import br.com.nomar.controlai.domain.groups.exception.PersonalGroupHasDataException
import br.com.nomar.controlai.domain.groups.entity.Group
import br.com.nomar.controlai.domain.groups.entity.GroupMember
import br.com.nomar.controlai.domain.groups.usecase.AcceptInviteUseCase
import br.com.nomar.controlai.domain.groups.usecase.DeclineInviteUseCase
import br.com.nomar.controlai.domain.groups.usecase.InviteToGroupUseCase
import br.com.nomar.controlai.domain.groups.usecase.LeaveGroupUseCase
import br.com.nomar.controlai.domain.groups.usecase.ListPendingInvitesUseCase
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class GroupInviteUseCasesTest {

    private val now: Instant = Instant.now()

    private fun pendingInvite(id: Long = 1L, groupId: Long = 10L, expiresAt: Instant = now.plus(Duration.ofDays(7))) =
        GroupInvite(
            id = id,
            groupId = groupId,
            inviterUserId = 99L,
            inviteeEmail = "invitee@test.com",
            status = InviteStatus.PENDING,
            token = "test-token",
            expiresAt = expiresAt,
        )

    private val fakeEmail = object : EmailGateway {
        override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String) = Result.success(Unit)
        override fun sendGroupInvite(toEmail: String, inviteLink: String) = Result.success(Unit)
    }

    // --- InviteToGroupUseCase ---

    @Test
    fun `InviteToGroupUseCase creates invite and sends email`() {
        var emailSent = false
        val useCase = InviteToGroupUseCase(
            findUserByEmailGateway = { Result.success(null) },
            findUserGroupGateway = { Result.success(null) },
            createGroupInviteGateway = { Result.success(it.copy(id = 1L)) },
            emailGateway = object : EmailGateway {
                override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String) = Result.success(Unit)
                override fun sendGroupInvite(toEmail: String, inviteLink: String): Result<Unit> {
                    emailSent = true
                    return Result.success(Unit)
                }
            },
            appWebUrl = "https://app.controlai.com",
        )

        val result = useCase.execute(inviterUserId = 1L, inviterGroupId = 10L, inviteeEmail = "invitee@test.com")

        assertTrue(result.isSuccess)
        assertTrue(emailSent)
    }

    @Test
    fun `InviteToGroupUseCase normalizes email to lowercase`() {
        var savedEmail: String? = null
        val useCase = InviteToGroupUseCase(
            findUserByEmailGateway = { Result.success(null) },
            findUserGroupGateway = { Result.success(null) },
            createGroupInviteGateway = { invite ->
                savedEmail = invite.inviteeEmail
                Result.success(invite.copy(id = 1L))
            },
            emailGateway = fakeEmail,
            appWebUrl = "https://app.controlai.com",
        )

        useCase.execute(inviterUserId = 1L, inviterGroupId = 10L, inviteeEmail = "  INVITEE@Test.COM  ")

        assertEquals("invitee@test.com", savedEmail)
    }

    @Test
    fun `InviteToGroupUseCase fails when invitee already in the same group`() {
        val inviterGroupId = 10L
        val useCase = InviteToGroupUseCase(
            findUserByEmailGateway = { Result.success(User(id = 2L, name = "Invitee", email = it)) },
            findUserGroupGateway = { Result.success(Group(id = inviterGroupId, name = "Shared")) },
            createGroupInviteGateway = { Result.success(it.copy(id = 1L)) },
            emailGateway = fakeEmail,
            appWebUrl = "https://app.controlai.com",
        )

        val result = useCase.execute(inviterUserId = 1L, inviterGroupId = inviterGroupId, inviteeEmail = "invitee@test.com")

        assertTrue(result.isFailure)
        assertIs<InviteeAlreadyInGroupException>(result.exceptionOrNull())
    }

    @Test
    fun `InviteToGroupUseCase succeeds even when email gateway fails (non-blocking)`() {
        val useCase = InviteToGroupUseCase(
            findUserByEmailGateway = { Result.success(null) },
            findUserGroupGateway = { Result.success(null) },
            createGroupInviteGateway = { Result.success(it.copy(id = 1L)) },
            emailGateway = object : EmailGateway {
                override fun sendPasswordReset(toEmail: String, toName: String, resetLink: String) = Result.success(Unit)
                override fun sendGroupInvite(toEmail: String, inviteLink: String): Result<Unit> =
                    Result.failure(RuntimeException("Resend down"))
            },
            appWebUrl = "https://app.controlai.com",
        )

        val result = useCase.execute(inviterUserId = 1L, inviterGroupId = 10L, inviteeEmail = "invitee@test.com")

        assertTrue(result.isSuccess)
    }

    // --- AcceptInviteUseCase ---

    @Test
    fun `AcceptInviteUseCase accepts pending invite without force when group is empty`() {
        var moved = false
        var statusUpdated = false
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(pendingInvite()) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(false) },
            deleteGroupDataGateway = { Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> moved = true; Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> statusUpdated = true; Result.success(pendingInvite().copy(status = InviteStatus.ACCEPTED)) },
        )

        val result = useCase.execute(inviteId = 1L, acceptingUserId = 2L, force = false)

        assertTrue(result.isSuccess)
        assertTrue(moved)
        assertTrue(statusUpdated)
    }

    @Test
    fun `AcceptInviteUseCase blocks when group has data and force is false`() {
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(pendingInvite()) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(true) },
            deleteGroupDataGateway = { Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> Result.success(pendingInvite()) },
        )

        val result = useCase.execute(inviteId = 1L, acceptingUserId = 2L, force = false)

        assertTrue(result.isFailure)
        assertIs<PersonalGroupHasDataException>(result.exceptionOrNull())
    }

    @Test
    fun `AcceptInviteUseCase deletes group data and moves user when force is true`() {
        var dataDeleted = false
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(pendingInvite()) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(true) },
            deleteGroupDataGateway = { dataDeleted = true; Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> Result.success(pendingInvite().copy(status = InviteStatus.ACCEPTED)) },
        )

        val result = useCase.execute(inviteId = 1L, acceptingUserId = 2L, force = true)

        assertTrue(result.isSuccess)
        assertTrue(dataDeleted)
    }

    @Test
    fun `AcceptInviteUseCase fails when invite is not found`() {
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(null) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(false) },
            deleteGroupDataGateway = { Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> Result.success(pendingInvite()) },
        )

        val result = useCase.execute(inviteId = 99L, acceptingUserId = 2L)

        assertTrue(result.isFailure)
        assertIs<InviteNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun `AcceptInviteUseCase fails when invite is expired`() {
        val expiredInvite = pendingInvite(expiresAt = now.minus(Duration.ofHours(1)))
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(expiredInvite) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(false) },
            deleteGroupDataGateway = { Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> Result.success(expiredInvite) },
        )

        val result = useCase.execute(inviteId = 1L, acceptingUserId = 2L)

        assertTrue(result.isFailure)
        assertIs<InviteNotPendingException>(result.exceptionOrNull())
    }

    @Test
    fun `AcceptInviteUseCase fails when invite is already accepted`() {
        val acceptedInvite = pendingInvite().copy(status = InviteStatus.ACCEPTED)
        val useCase = AcceptInviteUseCase(
            findInviteByIdGateway = { Result.success(acceptedInvite) },
            findUserGroupGateway = { Result.success(Group(id = 20L, name = "Personal")) },
            groupHasDataGateway = { Result.success(false) },
            deleteGroupDataGateway = { Result.success(Unit) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
            updateInviteStatusGateway = { _, _ -> Result.success(acceptedInvite) },
        )

        val result = useCase.execute(inviteId = 1L, acceptingUserId = 2L)

        assertTrue(result.isFailure)
        assertIs<InviteNotPendingException>(result.exceptionOrNull())
    }

    // --- DeclineInviteUseCase ---

    @Test
    fun `DeclineInviteUseCase declines a pending invite`() {
        var statusUpdated = false
        val useCase = DeclineInviteUseCase(
            findInviteByIdGateway = { Result.success(pendingInvite()) },
            updateInviteStatusGateway = { _, status ->
                statusUpdated = true
                assertEquals(InviteStatus.DECLINED, status)
                Result.success(pendingInvite().copy(status = InviteStatus.DECLINED))
            },
        )

        val result = useCase.execute(inviteId = 1L)

        assertTrue(result.isSuccess)
        assertTrue(statusUpdated)
    }

    @Test
    fun `DeclineInviteUseCase fails when invite is not found`() {
        val useCase = DeclineInviteUseCase(
            findInviteByIdGateway = { Result.success(null) },
            updateInviteStatusGateway = { _, _ -> Result.success(pendingInvite()) },
        )

        val result = useCase.execute(inviteId = 99L)

        assertTrue(result.isFailure)
        assertIs<InviteNotFoundException>(result.exceptionOrNull())
    }

    @Test
    fun `DeclineInviteUseCase fails when invite is already declined`() {
        val declined = pendingInvite().copy(status = InviteStatus.DECLINED)
        val useCase = DeclineInviteUseCase(
            findInviteByIdGateway = { Result.success(declined) },
            updateInviteStatusGateway = { _, _ -> Result.success(declined) },
        )

        val result = useCase.execute(inviteId = 1L)

        assertTrue(result.isFailure)
        assertIs<InviteNotPendingException>(result.exceptionOrNull())
    }

    // --- LeaveGroupUseCase ---

    @Test
    fun `LeaveGroupUseCase creates new personal group when user leaves shared group`() {
        var newGroupCreated = false
        var userMoved = false
        val useCase = LeaveGroupUseCase(
            findUserByIdGateway = { Result.success(User(id = it, name = "Ramon", email = "ramon@test.com")) },
            findUserGroupGateway = { Result.success(Group(id = 10L, name = "Shared")) },
            countGroupMembersGateway = { Result.success(2) },
            createPersonalGroupGateway = { _, _ -> newGroupCreated = true; Result.success(99L) },
            moveUserToGroupGateway = { _, newGroupId -> userMoved = true; assertEquals(99L, newGroupId); Result.success(Unit) },
        )

        val result = useCase.execute(userId = 1L)

        assertTrue(result.isSuccess)
        assertTrue(newGroupCreated)
        assertTrue(userMoved)
        assertEquals(99L, result.getOrNull())
    }

    @Test
    fun `LeaveGroupUseCase fails when user is sole member (personal group)`() {
        val useCase = LeaveGroupUseCase(
            findUserByIdGateway = { Result.success(User(id = it, name = "Ramon", email = "ramon@test.com")) },
            findUserGroupGateway = { Result.success(Group(id = 10L, name = "Ramon")) },
            countGroupMembersGateway = { Result.success(1) },
            createPersonalGroupGateway = { _, _ -> Result.success(99L) },
            moveUserToGroupGateway = { _, _ -> Result.success(Unit) },
        )

        val result = useCase.execute(userId = 1L)

        assertTrue(result.isFailure)
        assertIs<CannotLeavePersonalGroupException>(result.exceptionOrNull())
    }

    // --- ListPendingInvitesUseCase ---

    @Test
    fun `ListPendingInvitesUseCase filters out expired invites`() {
        val fresh = pendingInvite(id = 1L, expiresAt = now.plus(Duration.ofDays(3)))
        val expired = pendingInvite(id = 2L, expiresAt = now.minus(Duration.ofHours(1)))
        val useCase = ListPendingInvitesUseCase(
            listPendingInvitesGateway = { Result.success(listOf(fresh, expired)) },
        )

        val result = useCase.execute("invitee@test.com")

        assertTrue(result.isSuccess)
        val invites = result.getOrNull()!!
        assertEquals(1, invites.size)
        assertEquals(1L, invites.first().id)
    }

    @Test
    fun `ListPendingInvitesUseCase normalizes email to lowercase`() {
        var receivedEmail: String? = null
        val useCase = ListPendingInvitesUseCase(
            listPendingInvitesGateway = { email ->
                receivedEmail = email
                Result.success(emptyList())
            },
        )

        useCase.execute("  INVITEE@Test.COM  ")

        assertEquals("invitee@test.com", receivedEmail)
    }

    // --- GroupInvite entity ---

    @Test
    fun `GroupInvite isPending returns true for pending non-expired invite`() {
        val invite = pendingInvite(expiresAt = now.plus(Duration.ofDays(1)))
        assertTrue(invite.isPending())
    }

    @Test
    fun `GroupInvite isPending returns false for expired invite`() {
        val invite = pendingInvite(expiresAt = now.minus(Duration.ofMinutes(1)))
        assertFalse(invite.isPending())
    }

    @Test
    fun `GroupInvite isPending returns false for declined invite`() {
        val invite = pendingInvite().copy(status = InviteStatus.DECLINED)
        assertFalse(invite.isPending())
    }
}
