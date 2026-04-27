package no.nav.sokos.skattekort.dto

import kotlinx.serialization.Serializable

@Serializable
data class NoekkelinformasjonResponse(
    val antallAvHver: Map<String, Int>,
)
