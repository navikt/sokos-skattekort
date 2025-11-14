package no.nav.sokos.skattekort.module.skattekort

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class BestillingBatch
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingsbatchId? = null,
        val status: String,
        val type: String, // "BESTILLING", "OPPDATERING"
        val bestillingsreferanse: String,
        val oppdatert: Instant,
        val opprettet: Instant,
        val dataSendt: String,
    )

@Serializable
@JvmInline
value class BestillingsbatchId(
    val id: Long,
)

enum class BestillingBatchStatus(
    val value: String,
) {
    Ny(value = "NY"),
    Ferdig(value = "FERDIG"),
    Feilet(value = "FEILET"),
}
