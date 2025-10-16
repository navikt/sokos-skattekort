package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.math.BigDecimal
import javax.xml.datatype.DatatypeFactory

object TestObjects {
    val skattekortMedForskuddstrekk =
        Skattekortmelding(
            2017,
            "21048200130",
            Resultatstatus.SKATTEKORTOPPLYSNINGER_OK,
            Skattekort(
                2017,
                DatatypeFactory.newInstance().newXMLGregorianCalendar("2017-04-15"),
                2017005,
                listOf(
                    Trekktabell(
                        Trekkode.LOENN_FRA_NAV,
                        Tabelltype.TREKKTABELL_FOR_LOENN,
                        "7131",
                        BigDecimal("32"),
                        BigDecimal("10.5"),
                    ),
                    Trekkprosent(
                        Trekkode.LOENN_FRA_BIARBEIDSGIVER,
                        BigDecimal("32"),
                    ),
                    Trekkprosent(
                        Trekkode.LOENN_FRA_NAV,
                        BigDecimal("17.8"),
                    ),
                ),
            ),
            emptyList(),
        )
    val skattekortIkkeTrekkpliktig =
        Skattekortmelding(
            2017,
            "12097100500",
            Resultatstatus.IKKE_TREKKPLIKT,
            null,
            emptyList(),
        )
}
