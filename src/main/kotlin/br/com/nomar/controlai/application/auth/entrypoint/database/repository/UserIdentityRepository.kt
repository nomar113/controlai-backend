package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.UserIdentityModel
import br.com.nomar.controlai.domain.auth.entity.IdentityProvider
import org.springframework.data.jpa.repository.JpaRepository

interface UserIdentityRepository : JpaRepository<UserIdentityModel, Long> {
    fun findByProviderAndSubject(provider: IdentityProvider, subject: String): UserIdentityModel?
    fun findByUserIdAndProvider(userId: Long, provider: IdentityProvider): UserIdentityModel?
}
