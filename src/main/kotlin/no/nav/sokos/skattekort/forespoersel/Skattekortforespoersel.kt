package no.nav.sokos.skattekort.forespoersel

import no.nav.sokos.skattekort.person.Foedselsnummer

data class Skattekortforespoersel(
    val forespoersel: Forespoersel,
    val aar: Int,
    val fnr: Foedselsnummer,
)
