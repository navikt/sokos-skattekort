package no.nav.sokos.skattekort.utsending

import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.serialization.kotlinx.json.DefaultJson

import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.Skattekort
import no.nav.sokos.skattekort.skattekort.Trekkode
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utsending.oppdragz.SkattekortFixedRecordFormatter

private val json =
    Json(DefaultJson) {
        isLenient = true
        prettyPrint = true
    }

/*
Denne testen er drevet av to sett data:
- et sett med skattekort som skatt sier inneholder et bredt utvalg av data
- et sett med referansedata som er laget ved å kjøre settet med skattekort gjennom serialiseringen til gamel os-eskatt

Forhåpentligvis vil dette gjenskape den gamle oppførselen bra.

Tanken er at vi, dersom vi ender med å bestemme oss for å endre serialiseringen, gjør endringen, lager et nytt testdatasett,
og så setter oss sammen med oppdrag z-gjengen for å validere at endringen ble bra.
 */
@OptIn(ExperimentalTime::class)
class SkattekortFixedRecordFormatterTest :
    FunSpec({
        test("gå gjennom alle skattekort og sjekk at vi får et stabilt svar") {
            val arbeidstakere: List<Arbeidstaker> =
                json
                    .decodeFromString<HentSkattekortResponse>(readFile("/oppdragz/skattekortsvar.json"))
                    .arbeidsgiver!!
                    .flatMap { it.arbeidstaker }
            val referanseverdier: Map<String, String> = Json.decodeFromString(readFile("/oppdragz/skattekortreferanser.json"))
            arbeidstakere.associate { arbeidstaker ->
                val skattekort = Skattekort(PersonId(0), arbeidstaker)
                val nyFormatering = SkattekortFixedRecordFormatter(skattekort, arbeidstaker.arbeidstakeridentifikator).format()
                val gammelFormatering = referanseverdier.get(arbeidstaker.arbeidstakeridentifikator)
                nyFormatering shouldBe gammelFormatering
                Pair(arbeidstaker.arbeidstakeridentifikator, nyFormatering)
            }
            // Kommentert ut for enkel oppdatering av referansedataene når vi eventuelt endrer serialiseringen
            //    .let { nyeReferanseVerdier ->
            //        File("src/test/resources/oppdragz/skattekortreferanser.json").writeText(json.encodeToString(nyeReferanseVerdier))
            //    }
        }

        test("vi kan serialisere et frikort med beløpsgrense") {
            val skattekort =
                Skattekort(
                    id = null,
                    personId = PersonId(value = 1),
                    utstedtDato = LocalDate.parse("2020-09-09"),
                    identifikator = "123",
                    inntektsaar = 2025,
                    kilde = "TJAFS",
                    resultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
                    opprettet = Instant.parse("2020-08-30T18:43:00.50Z"),
                    forskuddstrekkList =
                        listOf(
                            Frikort(Trekkode.LOENN_FRA_NAV, 7890),
                        ),
                    tilleggsopplysningList = listOf(),
                )
            val copybook = SkattekortFixedRecordFormatter(skattekort, "01010112345").format()
            copybook shouldBe
                "01010112345skattekortopplysningerOK                20252020-09-09123                                                         1Frikort     loennFraNAV                                                      0007890    "
        }
    })
