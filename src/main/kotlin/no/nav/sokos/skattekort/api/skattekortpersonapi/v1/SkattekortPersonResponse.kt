package no.nav.sokos.skattekort.api.skattekortpersonapi.v1

import kotlinx.serialization.Serializable

@Serializable
data class SkattekortPersonResponse(
    val data: List<Arbeidstaker>? = null,
    val message: String? = null,
)
