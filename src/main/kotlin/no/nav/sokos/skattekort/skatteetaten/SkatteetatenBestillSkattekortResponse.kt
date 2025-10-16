package no.nav.sokos.skattekort.skatteetaten

@kotlinx.serialization.Serializable
data class SkatteetatenBestillSkattekortResponse(
    val dialogReferanse: String,
    val bestillingsreferanse: String,
)
