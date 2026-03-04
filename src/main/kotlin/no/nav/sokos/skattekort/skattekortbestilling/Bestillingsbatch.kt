package no.nav.sokos.skattekort.skattekortbestilling

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.Serializable

data class Bestillingsbatch
    @OptIn(ExperimentalTime::class)
    constructor(
        val id: BestillingsbatchId? = null,
        val status: String,
        val type: BestillingsbatchType,
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

enum class BestillingsbatchStatus(
    val value: String,
) {
    NY(value = "NY"),
    FERDIG(value = "FERDIG"),
    FEILET(value = "FEILET"),
}
