package no.nav.sokos.skattekort.forespoersel

import no.nav.sokos.skattekort.person.Person

data class Skattekortforespoersel(
    val forespoersel: Forespoersel,
    val aar: Int,
    val person: Person,
)
