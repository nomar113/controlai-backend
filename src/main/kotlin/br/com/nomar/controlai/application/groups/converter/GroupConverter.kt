package br.com.nomar.controlai.application.groups.converter

import br.com.nomar.controlai.application.groups.entrypoint.database.model.GroupModel
import br.com.nomar.controlai.domain.groups.entity.Group
import org.springframework.stereotype.Component

@Component
class GroupConverter {

    fun toEntity(model: GroupModel) = Group(
        id = model.id,
        name = model.name,
    )
}
