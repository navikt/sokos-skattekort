package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

data class SkattekortPersonRequest(
    val fnr: String,
    val inntektsaar: String,
)
