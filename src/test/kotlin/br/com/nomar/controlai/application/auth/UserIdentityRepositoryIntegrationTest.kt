package br.com.nomar.controlai.application.auth

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserIdentityModel
import br.com.nomar.controlai.application.auth.entrypoint.database.repository.UserIdentityRepository
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.annotation.Transactional

@SpringBootTest
@Transactional
class UserIdentityRepositoryIntegrationTest {

    @Autowired
    private lateinit var userIdentityRepository: UserIdentityRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Test
    fun `saves and finds an identity by provider and subject`() {
        val userId = insertUser("identity-find@example.com")

        userIdentityRepository.save(
            UserIdentityModel(userId = userId, provider = IdentityProvider.APPLE, subject = "apple-sub-find"),
        )

        val found = userIdentityRepository.findByProviderAndSubject(IdentityProvider.APPLE, "apple-sub-find")
        assertNotNull(found)
        assertEquals(userId, found!!.userId)
        assertEquals(IdentityProvider.APPLE, found.provider)
        assertNull(found.appleRefreshTokenEnc)
        assertNotNull(found.createdAt)
    }

    @Test
    fun `finds an identity by user and provider`() {
        val userId = insertUser("identity-by-user@example.com")
        userIdentityRepository.save(
            UserIdentityModel(userId = userId, provider = IdentityProvider.GOOGLE, subject = "google-sub-by-user"),
        )

        val found = userIdentityRepository.findByUserIdAndProvider(userId, IdentityProvider.GOOGLE)

        assertEquals("google-sub-by-user", found?.subject)
        assertNull(userIdentityRepository.findByUserIdAndProvider(userId, IdentityProvider.APPLE))
    }

    @Test
    fun `same subject under another provider is a different identity`() {
        val userId = insertUser("identity-provider-scope@example.com")
        userIdentityRepository.save(
            UserIdentityModel(userId = userId, provider = IdentityProvider.GOOGLE, subject = "shared-subject"),
        )

        assertNull(userIdentityRepository.findByProviderAndSubject(IdentityProvider.APPLE, "shared-subject"))
    }

    private fun insertUser(email: String): Long {
        jdbcTemplate.update("INSERT INTO users (name, email) VALUES (?, ?)", "Identity Probe", email)
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long::class.java, email)!!
    }
}
