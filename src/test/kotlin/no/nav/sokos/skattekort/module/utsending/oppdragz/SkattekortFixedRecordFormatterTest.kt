package no.nav.sokos.skattekort.module.utsending.oppdragz

import javax.xml.datatype.DatatypeFactory

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Skattekort

fun arbeidstakerConverter(a: Arbeidstaker): Skattekortmelding =
    Skattekortmelding(
        a.inntektsaar.toLong(),
        a.arbeidstakeridentifikator,
        Resultatstatus.fromValue(a.resultatForSkattekort),
        skattekortConverter(a.skattekort, a.inntektsaar.toLong()),
        a.tilleggsopplysning?.map { Tilleggsopplysning.fromValue(it) } ?: emptyList(),
    )

fun skattekortConverter(
    s: Skattekort?,
    inntektsaar: Long,
): no.nav.sokos.skattekort.module.utsending.oppdragz.Skattekort? =
    if (s != null) {
        Skattekort(
            inntektsaar,
            DatatypeFactory.newInstance().newXMLGregorianCalendar(s.utstedtDato),
            s.skattekortidentifikator,
            s.forskuddstrekk.map { forskuddstrekkConverter(it) },
        )
    } else {
        null
    }

fun forskuddstrekkConverter(f: Forskuddstrekk): no.nav.sokos.skattekort.module.utsending.oppdragz.Forskuddstrekk =
    if (f.trekkprosent != null) {
        Trekkprosent(
            Trekkode.fromValue(f.trekkode),
            f.trekkprosent.prosentsats,
        )
    } else if (f.trekktabell != null) {
        Trekktabell(
            Trekkode.fromValue(f.trekkode),
            Tabelltype.TREKKTABELL_FOR_LOENN,
            f.trekktabell.tabellnummer,
            f.trekktabell.prosentsats,
        )
    } else {
        Frikort(Trekkode.fromValue(f.trekkode), f.frikort?.frikortbeloep)
    }

/*
Denne testen er drevet av to sett data:
- et sett med skattekort som skatt sier inneholder et bredt utvalg av data
- et sett med referansedata som er laget ved å kjøre settet med skattekort gjennom serialiseringen til gamel os-eskatt

Forhåpentligvis vil dette gjenskape den gamle oppførselen bra.

Tanken er at vi, dersom vi ender med å bestemme oss for å endre serialiseringen, gjør endringen, lager et nytt testdatasett,
og så setter oss sammen med oppdrag z-gjengen for å validere at endringen ble bra.
 */
class SkattekortFixedRecordFormatterDuplicatorTest :
    FunSpec({
        test("gå gjennom alle skattekort og sjekk at vi får et stabilt svar") {
            val arbeidstakere: List<Arbeidstaker> =
                Json
                    .decodeFromString<HentSkattekortResponse>(readFile("/oppdragz/skattekortsvar.json"))
                    .arbeidsgiver!!
                    .flatMap { it.arbeidstaker }
            val referanseverdier: Map<String, String> = Json.decodeFromString(readFile("/oppdragz/skattekortreferanser.json"))
            val nyeReferanseVerdier: Map<String, String> =
                arbeidstakere
                    .map { arbeidstaker ->
                        val skattekortmelding = arbeidstakerConverter(arbeidstaker)
                        val nyFormatering = SkattekortFixedRecordFormatter(skattekortmelding, "2025").format()
                        val gammelFormatering = referanseverdier.get(arbeidstaker.arbeidstakeridentifikator)
                        assertEquals(nyFormatering, gammelFormatering)
                        Pair(arbeidstaker.arbeidstakeridentifikator, nyFormatering)
                    }.toMap()
            // Kommentert ut for enkel oppdatering av referansedataene når vi eventuelt endrer serialiseringen
            // File("src/test/resources/oppdragz/skattekortreferanser.json").writeText(Json { prettyPrint = true }.encodeToString(nyeReferanseVerdier))
        }
    })
