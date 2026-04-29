package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.utsending.Utsending

@Serializable
data class UtsendingDTO(
    val id: Long,
    val fnr: String,
    val inntektsaar: Int,
    val forsystem: String,
    val failCount: Int,
    val failMessage: String? = null,
    val opprettet: Instant,
) {
    companion object {
        fun fromDomain(utsending: Utsending): UtsendingDTO =
            UtsendingDTO(
                id = utsending.id?.value ?: error("Utsending mangler ID"),
                fnr = utsending.fnr.value,
                inntektsaar = utsending.inntektsaar,
                forsystem = utsending.forsystem.value,
                failCount = utsending.failCount,
                failMessage = utsending.failMessage,
                opprettet = utsending.opprettet,
            )
    }
}
