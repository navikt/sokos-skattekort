package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.math.BigDecimal
import javax.xml.datatype.DatatypeFactory

import no.nav.sokos.skattekort.module.skattekort.Prosentkort
import no.nav.sokos.skattekort.module.skattekort.Tabellkort

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
        tilleggsopplysning = sk.tilleggsopplysningList.map { Tilleggsopplysning.fromValue(it.value) },
    )
}
