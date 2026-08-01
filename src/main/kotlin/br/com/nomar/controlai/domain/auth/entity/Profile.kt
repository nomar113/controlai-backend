package br.com.nomar.controlai.domain.auth.entity

import br.com.nomar.controlai.domain.groups.entity.Group

class Profile(
    val user: User,
    val group: Group,
)
