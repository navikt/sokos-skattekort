package no.nav.sokos.skattekort.person

import java.time.Instant

import kotliquery.Row

const val AUDIT_SYSTEM = "system"

data class Audit(
    val id: AuditId? = null,
    val personId: PersonId,
    val brukerId: String,
    val opprettet: Instant = Instant.now(),
    val tag: AuditTag = AuditTag.OPPRETTET_PERSON,
    val informasjon: String?,
) {
    constructor(row: Row) : this(
        id = AuditId(row.long("id")),
        personId = PersonId(row.long("person_id")),
        brukerId = row.string("bruker_id"),
        opprettet = row.instant("opprettet"),
        tag = AuditTag.fromValue(row.string("tag")),
        informasjon = row.string("informasjon"),
    )
}

@JvmInline
value class AuditId(
    val value: Long,
)
