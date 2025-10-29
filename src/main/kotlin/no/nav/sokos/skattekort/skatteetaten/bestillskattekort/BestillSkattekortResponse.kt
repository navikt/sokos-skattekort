package no.nav.sokos.skattekort.skatteetaten.bestillskattekort

import kotlinx.serialization.Serializable

@Serializable
data class BestillSkattekortResponse(
    val dialogreferanse: String,
    val bestillingsreferanse: String,
)
