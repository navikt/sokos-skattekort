package no.nav.sokos.skattekort.api.model

import kotlin.time.Instant
import kotlinx.serialization.Serializable

import no.nav.sokos.skattekort.skattekortbestilling.Bestillingsbatch
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchStatus
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchType

@Serializable
data class BestillingsbatchDTO(
    val id: Long,
    val status: BestillingsbatchStatus,
    val type: BestillingsbatchType,
    val bestillingsreferanse: String,
    val oppdatert: Instant,
    val opprettet: Instant,
    val dataSendt: String,
    val dataMottatt: String? = null,
) {
    companion object {
        fun toDto(
            bestillingsbatch: Bestillingsbatch,
            dataMottatt: String?,
        ): BestillingsbatchDTO =
            BestillingsbatchDTO(
                id = bestillingsbatch.id?.id ?: error("Bestillingsbatch mangler ID"),
                status = bestillingsbatch.status,
                type = bestillingsbatch.type,
                bestillingsreferanse = bestillingsbatch.bestillingsreferanse,
                oppdatert = bestillingsbatch.oppdatert,
                opprettet = bestillingsbatch.opprettet,
                dataSendt = bestillingsbatch.dataSendt,
                dataMottatt = dataMottatt,
            )
    }
}
