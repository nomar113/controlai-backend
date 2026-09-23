package br.com.nomar.controlai.domain.account_deletion.entity

enum class PurgeOutcome {
    // The user was the group's only member: the group and all its financial data are gone
    SOLE_MEMBER_GROUP_PURGED,

    // The group stays with the remaining members; only the user and their personal data are gone
    MEMBER_REMOVED,

    // The deletion was cancelled after the job listed the account, so nothing was deleted
    NO_LONGER_DUE,
}
