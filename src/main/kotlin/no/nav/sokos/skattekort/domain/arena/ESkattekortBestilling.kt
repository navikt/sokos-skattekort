package no.nav.sokos.skattekort.domain.arena

import java.time.LocalDateTime

data class ESkattekortBestilling(
    val bestiller: Applikasjon,
    val inntektsaar: String,
    val bestillingId: Int? = null,
    val kvittering: Receipt? = null,
    val datoSendt: LocalDateTime? = null,
    val brukere: List<String> = emptyList(), // each fnr: 11 digits
    val antallForsok: Int = 0,
)
