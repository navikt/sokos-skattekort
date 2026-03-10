package no.nav.sokos.skattekort.api.model

import kotlinx.serialization.Serializable

@Serializable
data class WrappedWithErrorResponse<T>(
    val data: T,
    val errorMessage: String? = null,
)
