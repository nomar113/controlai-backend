package br.com.nomar.controlai.application.payment_methods.entrypoint.database.repository

import br.com.nomar.controlai.application.payment_methods.entrypoint.database.model.HolderModel
import org.springframework.data.jpa.repository.JpaRepository

interface HolderRepository : JpaRepository<HolderModel, Long> {
    fun findAllByOrderByNameAsc(): List<HolderModel>
}
