package no.nav.sokos.skattekort.bestilling

import no.nav.sokos.skattekort.person.AktoerId

data class Bestilling(
    val person_id: AktoerId?,
    val bestiller: String,
    val inntektYear: String,
    val fnr: String,
)
