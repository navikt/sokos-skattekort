package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.person.Audit
import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType

@Serializable
data class BatchInsightRequest(
    val tidspunktFom: Instant?,
    val tidspunktTom: Instant?,
    val status: BestillingsbatchStatus?,
    val type: BestillingsbatchType?,
)

typealias BatchInsightResponse = ListResponse<Bestillingsbatch>
typealias AuditResponse = ListResponse<Audit>

@Serializable
data class ListResponse<T>(
    val items: List<T>,
)
