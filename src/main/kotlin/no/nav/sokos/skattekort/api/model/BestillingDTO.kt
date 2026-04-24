package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.skattekorthenting.Bestilling

@Serializable
data class BestillingDTO(
    val id: Long,
    val personId: Long,
    val fnr: String,
    val inntektsaar: Int,
    val bestillingsbatchId: Long? = null,
    val oppdatert: Instant,
) {
    companion object {
        fun fromDomain(bestilling: Bestilling): BestillingDTO =
            BestillingDTO(
                id = bestilling.id?.id ?: error("Bestilling mangler ID"),
                personId = bestilling.personId.value,
                fnr = bestilling.fnr.value,
                inntektsaar = bestilling.inntektsaar,
                bestillingsbatchId = bestilling.bestillingsbatchId?.id,
                oppdatert = bestilling.oppdatert,
            )
    }
}
