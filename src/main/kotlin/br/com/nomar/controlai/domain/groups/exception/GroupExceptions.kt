package br.com.nomar.controlai.domain.groups.exception

class InviteNotFoundException(message: String = "Convite nao encontrado ou expirado") : RuntimeException(message)

class InviteNotPendingException(message: String = "Convite nao esta pendente") : RuntimeException(message)

class InviteeAlreadyInGroupException(message: String = "Usuario ja pertence ao grupo") : RuntimeException(message)

class PersonalGroupHasDataException(message: String = "Grupo pessoal possui dados; confirme com force=true para descartar") : RuntimeException(message)

class NotInGroupException(message: String = "Usuario nao pertence a este grupo") : RuntimeException(message)

class CannotLeavePersonalGroupException(message: String = "Nao e possivel sair de um grupo pessoal") : RuntimeException(message)
