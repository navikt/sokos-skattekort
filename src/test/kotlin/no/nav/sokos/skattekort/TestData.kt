package no.nav.sokos.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

import no.nav.sokos.skattekort.module.person.Foedselsnummer
import no.nav.sokos.skattekort.module.person.FoedselsnummerId
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Frikort
import no.nav.sokos.skattekort.module.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortId
import no.nav.sokos.skattekort.module.skattekort.Tabellkort
import no.nav.sokos.skattekort.module.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.module.utsending.oppdragz.Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE
import no.nav.sokos.skattekort.module.utsending.oppdragz.Trekkode

object TestData {
    @OptIn(ExperimentalTime::class)
    fun getSkattekortTestData() =
        Skattekort(
            id = SkattekortId(1L),
            personId = PersonId(5L),
            utstedtDato = LocalDate.parse("2025-04-30"),
            identifikator = "20860599016",
            inntektsaar = 2025,
            kilde = "skattekortsvar",
            resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
            opprettet = Clock.System.now(),
            forskuddstrekkList = getForskuddstrekkListTestData(),
            tilleggsopplysningList = emptyList(),
        )

    fun getPersonTestData() =
        Person(
            id = PersonId(5L),
            flagget = false,
            foedselsnummer =
                Foedselsnummer(
                    id = FoedselsnummerId(10L),
                    personId = PersonId(5L),
                    gjelderFom = LocalDate.parse("2020-01-01"),
                    fnr = Personidentifikator("20860599016"),
                ),
        )

    fun getForskuddstrekkListTestData() =
        listOf(
            Frikort(
                trekkode = Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER.value,
                frikortBeloep = 0,
            ),
            Tabellkort(
                trekkode = Trekkode.LOENN_FRA_NAV.value,
                tabellNummer = "7100",
                prosentSats = 35.0.toBigDecimal(),
                antallMndForTrekk = 0.5.toBigDecimal(),
            ),
        )

    fun getTilleggsopplysningListTestData() =
        listOf(
            Tilleggsopplysning(
                opplysning = OPPHOLD_I_TILTAKSSONE.value,
            ),
        )
}
