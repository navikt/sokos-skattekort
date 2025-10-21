package no.nav.sokos.skattekort.skatteetaten

@kotlinx.serialization.Serializable
data class SkatteetatenBestillSkattekortResponse(
    val dialogreferanse: String,
    val bestillingsreferanse: String,
)
