package no.nav.sokos.skattekort.person

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

import kotliquery.Row

const val AUDIT_SYSTEM = "system"

data class Audit(
    val id: AuditId? = null,
    val personId: PersonId,
    val brukerId: String,
    val opprettet: Instant = Clock.System.now(),
    val tag: AuditTag = AuditTag.OPPRETTET_PERSON,
    val informasjon: String?,
) {
    constructor(row: Row) : this(
        id = AuditId(row.long("id")),
        personId = PersonId(row.long("person_id")),
        brukerId = row.string("bruker_id"),
        opprettet = row.instant("opprettet").toKotlinInstant(),
        tag = AuditTag.fromValue(row.string("tag")),
        informasjon = row.string("informasjon"),
    )
}

@JvmInline
value class AuditId(
    val id: Long,
)
