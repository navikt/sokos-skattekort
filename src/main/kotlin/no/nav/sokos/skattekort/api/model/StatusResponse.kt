package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.skattekortbestilling.Status

@Serializable
data class StatusResponse(
    val status: Status,
)
