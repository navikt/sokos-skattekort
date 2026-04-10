package no.nav.sokos.skattekort.skattekortbestilling

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Bestillingsbatch
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingsbatchId? = null,
        val status: BestillingsbatchStatus,
        val type: BestillingsbatchType,
        val bestillingsreferanse: String,
        val oppdatert: Instant,
        val opprettet: Instant,
        val dataSendt: String,
        val dataMottatt: String? = null,
    )

@Serializable
@JvmInline
value class BestillingsbatchId(
    val id: Long,
)
