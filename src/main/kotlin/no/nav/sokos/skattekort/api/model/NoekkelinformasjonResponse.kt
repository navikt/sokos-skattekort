package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

@Serializable
data class NoekkelinformasjonResponse(
    val antallAvHver: Map<String, Int>,
)
