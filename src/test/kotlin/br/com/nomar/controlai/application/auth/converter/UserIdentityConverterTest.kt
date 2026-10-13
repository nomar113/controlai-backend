package br.com.nomar.controlai.application.auth.converter

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserIdentityModel
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import br.com.nomar.controlai.domain.auth.entity.UserIdentity
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class UserIdentityConverterTest {

    private val converter = UserIdentityConverter()

    @Test
    fun `maps every field from model to entity`() {
        val model = UserIdentityModel(
            id = 7,
            userId = 42,
            provider = IdentityProvider.APPLE,
            subject = "apple-sub-001",
            appleRefreshTokenEnc = "cipher",
            createdAt = Instant.parse("2026-10-01T10:00:00Z"),
        )

        val entity = converter.toEntity(model)

        assertEquals(7L, entity.id)
        assertEquals(42L, entity.userId)
        assertEquals(IdentityProvider.APPLE, entity.provider)
        assertEquals("apple-sub-001", entity.subject)
        assertEquals("cipher", entity.appleRefreshTokenEnc)
        assertEquals(Instant.parse("2026-10-01T10:00:00Z"), entity.createdAt)
    }

    @Test
    fun `maps every field from entity to model`() {
        val entity = UserIdentity(
            id = 9,
            userId = 3,
            provider = IdentityProvider.GOOGLE,
            subject = "google-sub-009",
            appleRefreshTokenEnc = null,
            createdAt = Instant.parse("2026-10-02T08:30:00Z"),
        )

        val model = converter.toModel(entity)

        assertEquals(9L, model.id)
        assertEquals(3L, model.userId)
        assertEquals(IdentityProvider.GOOGLE, model.provider)
        assertEquals("google-sub-009", model.subject)
        assertEquals(null, model.appleRefreshTokenEnc)
        assertEquals(Instant.parse("2026-10-02T08:30:00Z"), model.createdAt)
    }

    @Test
    fun `round trip from model to entity and back keeps the same values`() {
        val model = UserIdentityModel(
            id = 1,
            userId = 5,
            provider = IdentityProvider.GOOGLE,
            subject = "google-sub-005",
        )

        assertEquals(model, converter.toModel(converter.toEntity(model)))
    }
}
