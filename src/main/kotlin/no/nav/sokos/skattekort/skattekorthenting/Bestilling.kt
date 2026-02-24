package no.nav.sokos.skattekort.skattekorthenting

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchId

data class Bestilling
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingId? = null,
        val personId: PersonId,
        val fnr: Personidentifikator,
        val inntektsaar: Int,
        val bestillingsbatchId: BestillingsbatchId? = null,
        val oppdatert: Instant = Clock.System.now(),
    )

@Serializable
@JvmInline
value class BestillingId(
    val id: Long,
)
