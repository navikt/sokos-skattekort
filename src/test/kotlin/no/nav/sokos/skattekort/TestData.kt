package no.nav.sokos.skattekort

import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.datetime.LocalDate

import no.nav.sokos.skattekort.module.person.Foedselsnummer
import no.nav.sokos.skattekort.module.person.FoedselsnummerId
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Skattekort
import no.nav.sokos.skattekort.module.skattekort.SkattekortDel
import no.nav.sokos.skattekort.module.skattekort.SkattekortDelId
import no.nav.sokos.skattekort.module.skattekort.SkattekortId
import no.nav.sokos.skattekort.module.skattekort.SkattekortTileggsopplysning
import no.nav.sokos.skattekort.module.skattekort.SkattekortTileggsopplysningId
import no.nav.sokos.skattekort.module.skattekort.SkattekortType
import no.nav.sokos.skattekort.module.utsending.oppdragz.Tilleggsopplysning
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
            opprettet = Clock.System.now(),
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

    fun getSkatteDelListTestData() =
        listOf(
            SkattekortDel(
                id = SkattekortDelId(2L),
                skattekortId = SkattekortId(1L),
                trekkKode = Trekkode.LOENN_FRA_HOVEDARBEIDSGIVER.value,
                skattekortType = SkattekortType.FRIKORT,
                frikortBeloep = 0,
            ),
            SkattekortDel(
                id = SkattekortDelId(1L),
                skattekortId = SkattekortId(1L),
                trekkKode = Trekkode.LOENN_FRA_NAV.value,
                skattekortType = SkattekortType.TABELL,
                tabellNummer = "7100",
                prosentsats = 35.0,
                frikortBeloep = null,
            ),
        )

    fun getSkattekortTileggsopplysningListTestData() =
        listOf(
            SkattekortTileggsopplysning(
                id = SkattekortTileggsopplysningId(1L),
                skattekortId = SkattekortId(1L),
                opplysning = Tilleggsopplysning.OPPHOLD_I_TILTAKSSONE.value,
            ),
        )
}
