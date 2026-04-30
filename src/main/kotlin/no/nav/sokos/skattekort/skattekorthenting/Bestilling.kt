package no.nav.sokos.skattekort.skattekorthenting

import kotlin.time.Clock
import kotlin.time.Instant

import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchId

data class Bestilling(
    val id: BestillingId? = null,
    val personId: PersonId,
    val fnr: Personidentifikator,
    val inntektsaar: Int,
    val bestillingsbatchId: BestillingsbatchId? = null,
    val oppdatert: Instant = Clock.System.now(),
)

@JvmInline
value class BestillingId(
    val id: Long,
)
