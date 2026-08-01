package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.RefreshTokenModel
import org.springframework.data.jpa.repository.JpaRepository

interface RefreshTokenRepository : JpaRepository<RefreshTokenModel, Long> {
    fun findByTokenHash(tokenHash: String): RefreshTokenModel?
}
