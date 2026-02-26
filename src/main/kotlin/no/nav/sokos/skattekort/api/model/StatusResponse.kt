package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.module.skattekort.Status

@Serializable
data class StatusResponse(
    val status: Status,
)
