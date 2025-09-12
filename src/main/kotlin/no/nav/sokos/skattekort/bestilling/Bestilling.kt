package no.nav.sokos.skattekort.bestilling

import no.nav.sokos.skattekort.person.PersonId

data class Bestilling(
    val person_id: PersonId?,
    val bestiller: String,
    val inntektYear: String,
    val fnr: String,
)
