package br.com.nomar.controlai.application.auth.entrypoint.database.repository

import br.com.nomar.controlai.application.auth.entrypoint.database.model.ApiKeyModel
import org.springframework.data.jpa.repository.JpaRepository

interface ApiKeyRepository : JpaRepository<ApiKeyModel, Long> {
    fun findByGroupId(groupId: Long): ApiKeyModel?
    fun findByKeyHash(keyHash: String): ApiKeyModel?
}
