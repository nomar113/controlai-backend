package br.com.nomar.controlai.application.groups.entrypoint.database.repository

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupModel
import org.springframework.data.jpa.repository.JpaRepository

interface GroupRepository : JpaRepository<GroupModel, Long>
