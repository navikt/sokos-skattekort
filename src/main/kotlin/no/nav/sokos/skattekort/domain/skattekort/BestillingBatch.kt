package no.nav.sokos.skattekort.domain.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class BestillingBatch
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingBatchId? = null,
        val status: String,
        val bestillingsreferanse: String,
        val oppdatert: Instant,
        val dataSendt: String,
    )

@Serializable
@JvmInline
value class BestillingBatchId(
    val id: Long,
)
