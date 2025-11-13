package no.nav.sokos.skattekort.module.skattekortpersonapi.v1

import kotlinx.serialization.Serializable

@Serializable
data class SkattekortPersonRequest(
    val fnr: String,
    val inntektsaar: String,
)
