package no.nav.sokos.skattekort.forespoersel

import no.nav.sokos.skattekort.person.Person

data class Forespoersel(
    val forsystem: Forsystem,
    val inntektYear: Int,
    val persons: List<Person>,
)
