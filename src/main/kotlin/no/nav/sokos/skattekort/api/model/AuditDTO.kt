package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlin.time.toKotlinInstant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.person.Audit
import no.nav.sokos.skattekort.person.AuditTag

@Serializable
data class AuditDTO(
    val id: Long?,
    val personId: Long,
    val brukerId: String,
    val opprettet: Instant,
    val tag: AuditTag = AuditTag.OPPRETTET_PERSON,
    val informasjon: String?,
) {
    constructor(audit: Audit) : this(
        id = audit.id?.value,
        personId = audit.personId.value,
        brukerId = audit.brukerId,
        opprettet = audit.opprettet.toKotlinInstant(),
        tag = audit.tag,
        informasjon = audit.informasjon,
    )
}
