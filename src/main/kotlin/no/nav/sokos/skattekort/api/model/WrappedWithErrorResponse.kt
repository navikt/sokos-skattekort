package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

@Serializable
data class WrappedWithErrorResponse<T>(
    var data: List<T> = emptyList(),
    var errorMessage: String? = null,
)
