package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.math.BigDecimal
import javax.xml.datatype.DatatypeFactory

import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.skattekort.Trekkode

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
                forskuddstrekk =
                    sk.forskuddstrekkList.map {
                        when (it) {
                            is no.nav.sokos.skattekort.module.skattekort.Frikort -> Frikort(Trekkode.fromValue(it.trekkode.value), it.frikortBeloep?.let { belop -> BigDecimal(belop) })
                            is Prosentkort -> Trekkprosent(Trekkode.fromValue(it.trekkode.value), it.prosentSats, it.antallMndForTrekk)
                            is Tabellkort -> Trekktabell(Trekkode.fromValue(it.trekkode.value), Tabelltype.TREKKTABELL_FOR_LOENN, it.tabellNummer, it.prosentSats, it.antallMndForTrekk)
                        }
                    },
            ),
        tilleggsopplysning =
            sk.tilleggsopplysningList,
    )
}
