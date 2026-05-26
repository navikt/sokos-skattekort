package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

@Serializable
data class DetailStatusResponse(
    val statuses: Map<String, DetailStatus>,
)
