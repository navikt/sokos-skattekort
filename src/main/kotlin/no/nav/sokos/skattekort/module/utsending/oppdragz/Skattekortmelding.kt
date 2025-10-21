package no.nav.sokos.skattekort.module.utsending.oppdragz

import javax.xml.datatype.DatatypeFactory

data class Skattekortmelding(
    val inntektsaar: Long = 0,
    val arbeidstakeridentifikator: String? = null,
    val resultatPaaForespoersel: Resultatstatus,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<Tilleggsopplysning> = listOf<Tilleggsopplysning>(),
) {
    constructor(sk: no.nav.sokos.skattekort.module.skattekort.Skattekort, forespurtFnr: String) : this(
        inntektsaar = sk.inntektsaar.toLong(),
        arbeidstakeridentifikator = forespurtFnr,
        resultatPaaForespoersel = Resultatstatus.SKATTEKORTOPPLYSNINGER_OK,
        skattekort =
            Skattekort(
                inntektsaar = sk.inntektsaar.toLong(),
                utstedtDato = DatatypeFactory.newInstance().newXMLGregorianCalendar(sk.utstedtDato.toString()),
                skattekortidentifikator = sk.identifikator.toLong(),
                forskuddstrekk = sk.deler.map { Forskuddstrekk(it) },
            ),
        tilleggsopplysning = sk.tilleggsopplysning.map { Tilleggsopplysning(it) },
    )
}
