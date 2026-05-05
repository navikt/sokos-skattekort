package no.nav.sokos.skattekort.skattekortbestilling

import java.time.Instant

data class Bestillingsbatch(
    val id: BestillingsbatchId? = null,
    val status: BestillingsbatchStatus,
    val type: BestillingsbatchType,
    val bestillingsreferanse: String,
    val oppdatert: Instant,
    val opprettet: Instant,
    val dataSendt: String,
)

@JvmInline
value class BestillingsbatchId(
    val id: Long,
)
