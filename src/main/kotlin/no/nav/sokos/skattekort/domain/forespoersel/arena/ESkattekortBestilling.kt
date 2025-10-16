package no.nav.sokos.skattekort.domain.forespoersel.arena

import java.time.LocalDateTime

data class ESkattekortBestilling(
    val bestiller: Applikasjon,
    val inntektsaar: String,
    val bestillingId: Int? = null,
    val kvittering: Receipt? = null,
    val datoSendt: LocalDateTime? = null,
    val brukere: List<String> = emptyList(),
    val antallForsok: Int = 0,
)
