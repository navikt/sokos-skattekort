package no.nav.sokos.skattekort.domain.person

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Audit
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: AuditId? = null,
        val personId: PersonId,
        val brukerId: String,
        val opprettet: Instant = Clock.System.now(),
        val tag: AuditTag = AuditTag.OPPRETTET_PERSON,
        val informasjon: String?,
    )

@Serializable
@JvmInline
value class AuditId(
    val id: Long,
)
