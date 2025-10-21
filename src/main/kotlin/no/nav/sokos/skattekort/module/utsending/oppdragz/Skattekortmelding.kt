package no.nav.sokos.skattekort.module.utsending.oppdragz

data class Skattekortmelding(
    val inntektsaar: Long,
    val arbeidstakeridentifikator: String? = null,
    val resultatPaaForespoersel: Resultatstatus,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<Tilleggsopplysning> = listOf<Tilleggsopplysning>(),
)
