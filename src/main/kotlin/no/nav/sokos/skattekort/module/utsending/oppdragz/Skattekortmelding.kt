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
                utstedtDato = DatatypeFactory.newInstance().newXMLGregorianCalendar(sk.utstedtDato.toString()),
                skattekortidentifikator = sk.identifikator.toLong(),
                forskuddstrekk =
                    sk.forskuddstrekkList.map {
                        when (it) {
                            is no.nav.sokos.skattekort.module.skattekort.Frikort -> Frikort(Trekkode.fromValue(it.trekkode), BigDecimal(it.frikortBeloep))
                            is Prosentkort -> Trekkprosent(Trekkode.fromValue(it.trekkode), it.prosentSats, it.antallMndForTrekk)
                            is Tabellkort -> Trekktabell(Trekkode.fromValue(it.trekkode), Tabelltype.TREKKTABELL_FOR_LOENN, it.tabellNummer, it.prosentSats, it.antallMndForTrekk)
                            else -> throw IllegalStateException("Unexpected $it")
                        }
                    },
            ),
        tilleggsopplysning = sk.tilleggsopplysningList.map { Tilleggsopplysning.fromValue(it.opplysning) },
    )
}
