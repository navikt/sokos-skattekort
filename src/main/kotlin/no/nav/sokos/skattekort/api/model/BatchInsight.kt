package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch

@Serializable
data class BatchInsightRequest(
    val tidspunktFom: Instant?,
    val tidspunktTom: Instant?,
)

@Serializable
data class BatchInsightResponse(
    val bestillingsbatcher: List<Bestillingsbatch>,
)
