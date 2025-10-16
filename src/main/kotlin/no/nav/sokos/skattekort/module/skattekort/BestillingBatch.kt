package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class BestillingBatch
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingsbatchId? = null,
        val status: String,
        val bestillingsreferanse: String,
        val oppdatert: Instant,
        val dataSendt: String,
    )

@Serializable
@JvmInline
value class BestillingsbatchId(
    val id: Long,
)
