package no.nav.sokos.skattekort.module.utsending.oppdragz

import javax.xml.datatype.DatatypeFactory

import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning

data class Skattekortmelding(
    val inntektsaar: Long = 0,
    val arbeidstakeridentifikator: String? = null,
    val resultatPaaForespoersel: ResultatForSkattekort,
    val skattekort: Skattekort? = null,
    val tilleggsopplysning: List<Tilleggsopplysning> = emptyList(),
) {
    constructor(sk: no.nav.sokos.skattekort.module.skattekort.Skattekort, forespurtFnr: String) : this(
        inntektsaar = sk.inntektsaar.toLong(),
        arbeidstakeridentifikator = forespurtFnr,
        resultatPaaForespoersel = ResultatForSkattekort.SkattekortopplysningerOK,
        skattekort =
            Skattekort(
                inntektsaar = sk.inntektsaar.toLong(),
                utstedtDato = sk.utstedtDato?.toString()?.let { DatatypeFactory.newInstance().newXMLGregorianCalendar(it) },
                skattekortidentifikator = sk.identifikator?.toLong(),
                forskuddstrekk = sk.forskuddstrekkList,
            ),
        tilleggsopplysning =
            sk.tilleggsopplysningList,
    )
}
