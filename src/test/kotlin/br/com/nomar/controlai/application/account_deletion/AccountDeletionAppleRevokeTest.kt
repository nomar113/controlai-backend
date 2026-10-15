package br.com.nomar.controlai.application.account_deletion

import br.com.nomar.controlai.domain.account_deletion.usecase.PurgeDueAccountsUseCase
import br.com.nomar.controlai.domain.account_deletion.usecase.RequestAccountDeletionUseCase
import br.com.nomar.controlai.domain.auth.gateway.AppleTokenExchangeGateway
import br.com.nomar.controlai.domain.auth.gateway.TokenCipherGateway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.anyString
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

// Account deletion with an Apple identity, against the real schema. Apple itself is replaced by the mock.
@SpringBootTest
class AccountDeletionAppleRevokeTest {

    @Autowired private lateinit var jdbcTemplate: JdbcTemplate
    @Autowired private lateinit var requestAccountDeletionUseCase: RequestAccountDeletionUseCase
    @Autowired private lateinit var purgeDueAccountsUseCase: PurgeDueAccountsUseCase
    @Autowired private lateinit var tokenCipherGateway: TokenCipherGateway

    @MockitoBean private lateinit var appleTokenExchangeGateway: AppleTokenExchangeGateway

    private val appleEmail = "apple-revoke-apple@controlai.test"
    private val googleEmail = "apple-revoke-google@controlai.test"

    private val due = Timestamp.from(Instant.now().minus(Duration.ofDays(1)))

    @BeforeEach
    fun setUp() {
        cleanUp()
        `when`(appleTokenExchangeGateway.revoke(anyString(), anyString())).thenReturn(Result.success(Unit))
    }

    @AfterEach
    fun cleanUp() {
        val userIds = jdbcTemplate.queryForList(
            "SELECT id FROM users WHERE email LIKE 'apple-revoke-%@controlai.test'",
            Long::class.java,
        )
        userIds.forEach { id ->
            jdbcTemplate.update("DELETE FROM user_identities WHERE user_id = ?", id)
            jdbcTemplate.update("DELETE FROM group_members WHERE user_id = ?", id)
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id)
        }
        jdbcTemplate.update("DELETE FROM `groups` WHERE name LIKE 'Apple Revoke %'")
    }

    // ---- Deletion request: Apple is asked to revoke right away, the link stays until the purge ----

    @Test
    fun `asking for the deletion revokes the Apple token once and clears it, keeping the link`() {
        val userId = insertUser(appleEmail, scheduledFor = null)
        insertAppleIdentity(userId, "com.nomar.controlai\nrt-123")

        requestAccountDeletionUseCase.execute(userId).getOrThrow()

        verify(appleTokenExchangeGateway, times(1)).revoke("rt-123", "com.nomar.controlai")
        assertEquals(1L, count("SELECT COUNT(*) FROM user_identities WHERE user_id = ?", userId))
        assertNull(storedCredential(userId))
    }

    @Test
    fun `a failed revocation at the request keeps the token so the purge retries it`() {
        `when`(appleTokenExchangeGateway.revoke(anyString(), anyString()))
            .thenReturn(Result.failure(IllegalStateException("network")))
        val userId = insertUser(appleEmail, scheduledFor = null)
        insertAppleIdentity(userId, "com.nomar.controlai\nrt-789")

        requestAccountDeletionUseCase.execute(userId).getOrThrow()

        assertNotNull(storedCredential(userId))
        `when`(appleTokenExchangeGateway.revoke(anyString(), anyString())).thenReturn(Result.success(Unit))
        // Moves the grace period to the past, so the purge job sees the account as due
        jdbcTemplate.update("UPDATE users SET deletion_scheduled_for = ? WHERE id = ?", due, userId)

        purgeDueAccountsUseCase.execute().getOrThrow()

        verify(appleTokenExchangeGateway, times(2)).revoke("rt-789", "com.nomar.controlai")
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
    }

    @Test
    fun `asking for the deletion of an account without an Apple identity never reaches Apple`() {
        val userId = insertUser(googleEmail, scheduledFor = null)
        jdbcTemplate.update(
            "INSERT INTO user_identities (user_id, provider, subject) VALUES (?, 'GOOGLE', ?)",
            userId, "apple-revoke-google-sub-$userId",
        )

        requestAccountDeletionUseCase.execute(userId).getOrThrow()

        verify(appleTokenExchangeGateway, never()).revoke(anyString(), anyString())
    }

    // ---- Purge: what is still stored when the account is removed is revoked after the commit ----

    @Test
    fun `purging an account with an Apple identity revokes its refresh token once and removes the link`() {
        val userId = insertUser(appleEmail, scheduledFor = due)
        insertAppleIdentity(userId, "com.nomar.controlai\nrt-123")

        purgeDueAccountsUseCase.execute().getOrThrow()

        verify(appleTokenExchangeGateway).revoke("rt-123", "com.nomar.controlai")
        assertEquals(0L, count("SELECT COUNT(*) FROM user_identities WHERE user_id = ?", userId))
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
    }

    @Test
    fun `a failed Apple revocation still removes the account and its link`() {
        `when`(appleTokenExchangeGateway.revoke(anyString(), anyString()))
            .thenReturn(Result.failure(IllegalStateException("invalid_grant")))
        val userId = insertUser(appleEmail, scheduledFor = due)
        insertAppleIdentity(userId, "com.nomar.controlai\nrt-456")

        purgeDueAccountsUseCase.execute().getOrThrow()

        assertEquals(0L, count("SELECT COUNT(*) FROM user_identities WHERE user_id = ?", userId))
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
    }

    @Test
    fun `an account whose token was already cleared by the request is purged without a second revocation`() {
        val userId = insertUser(appleEmail, scheduledFor = due)
        jdbcTemplate.update(
            "INSERT INTO user_identities (user_id, provider, subject) VALUES (?, 'APPLE', ?)",
            userId, "apple-revoke-sub-$userId",
        )

        purgeDueAccountsUseCase.execute().getOrThrow()

        verify(appleTokenExchangeGateway, never()).revoke(anyString(), anyString())
        assertEquals(0L, count("SELECT COUNT(*) FROM users WHERE id = ?", userId))
    }

    // A group of one member, and a user with or without a scheduled deletion
    private fun insertUser(email: String, scheduledFor: Timestamp?): Long {
        jdbcTemplate.update("INSERT INTO `groups` (name) VALUES (?)", "Apple Revoke $email")
        val groupId = lastInsertId()
        jdbcTemplate.update(
            "INSERT INTO users (name, email, deletion_requested_at, deletion_scheduled_for) VALUES (?, ?, ?, ?)",
            "Apple Revoke Member",
            email,
            scheduledFor?.let { Timestamp.from(it.toInstant().minus(Duration.ofDays(30))) },
            scheduledFor,
        )
        val userId = lastInsertId()
        jdbcTemplate.update("INSERT INTO group_members (group_id, user_id) VALUES (?, ?)", groupId, userId)
        return userId
    }

    // Stored the way the login stores it: encrypted, never in plain text
    private fun insertAppleIdentity(userId: Long, plainCredential: String) {
        jdbcTemplate.update(
            "INSERT INTO user_identities (user_id, provider, subject, apple_refresh_token_enc) VALUES (?, 'APPLE', ?, ?)",
            userId, "apple-revoke-sub-$userId", tokenCipherGateway.encrypt(plainCredential),
        )
    }

    private fun storedCredential(userId: Long): String? =
        jdbcTemplate.queryForObject(
            "SELECT apple_refresh_token_enc FROM user_identities WHERE user_id = ? AND provider = 'APPLE'",
            String::class.java,
            userId,
        )

    private fun lastInsertId(): Long = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long::class.java)!!

    private fun count(sql: String, vararg args: Any): Long = jdbcTemplate.queryForObject(sql, Long::class.java, *args)!!
}
