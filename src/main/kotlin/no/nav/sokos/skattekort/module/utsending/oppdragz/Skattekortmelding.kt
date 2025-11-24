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
                    sk.forskuddstrekkList.map { trekk ->
                        when (trekk) {
                            is no.nav.sokos.skattekort.module.skattekort.Frikort -> Frikort(Trekkode.fromValue(trekk.trekkode.value), BigDecimal(trekk.frikortBeloep))
                            is Prosentkort -> Trekkprosent(Trekkode.fromValue(trekk.trekkode.value), trekk.prosentSats, trekk.antallMndForTrekk)
                            is Tabellkort -> Trekktabell(Trekkode.fromValue(trekk.trekkode.value), Tabelltype.TREKKTABELL_FOR_LOENN, trekk.tabellNummer, trekk.prosentSats, trekk.antallMndForTrekk)
                        }
                    },
            ),
        tilleggsopplysning = sk.tilleggsopplysningList.map { Tilleggsopplysning.fromValue(it.value) },
    )
}
