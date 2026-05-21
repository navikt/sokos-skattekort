package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class BatchInsightRequest(
    val tidspunktFom: Instant?,
    val tidspunktTom: Instant?,
)

typealias BatchInsightResponse = ListResponse<BestillingsbatchDTO>
typealias AuditResponse = ListResponse<AuditDTO>
typealias BestillingResponse = ListResponse<BestillingDTO>
typealias UtsendingResponse = ListResponse<UtsendingDTO>

@Serializable
data class ListResponse<T>(
    val items: List<T>,
)
