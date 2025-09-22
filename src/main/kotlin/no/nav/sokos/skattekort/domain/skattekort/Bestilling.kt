package no.nav.sokos.skattekort.domain.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.domain.person.PersonId
import no.nav.sokos.skattekort.domain.person.Personidentifikator

data class Bestilling
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingId? = null,
        val personId: PersonId,
        val fnr: Personidentifikator,
        val aar: Int,
        val bestillingBatchId: BestillingBatchId? = null,
        val oppdatert: Instant = Clock.System.now(),
    )

@Serializable
@JvmInline
value class BestillingId(
    val id: Long,
)
