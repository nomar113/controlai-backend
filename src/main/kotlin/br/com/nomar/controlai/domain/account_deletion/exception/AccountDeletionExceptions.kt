package br.com.nomar.controlai.domain.account_deletion.exception

class AccountDeletionAlreadyScheduledException(message: String = "Exclusao da conta ja esta agendada") : RuntimeException(message)

class AccountDeletionNotScheduledException(message: String = "Nao ha exclusao de conta agendada") : RuntimeException(message)
