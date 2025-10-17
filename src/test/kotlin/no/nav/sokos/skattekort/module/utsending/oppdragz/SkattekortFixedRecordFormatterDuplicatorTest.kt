package no.nav.sokos.skattekort.module.utsending.oppdragz

import java.io.File
import javax.xml.datatype.DatatypeFactory

import kotlin.test.assertEquals
import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.module.bestilling.svar.Arbeidstaker
import no.nav.sokos.skattekort.module.bestilling.svar.Forskuddstrekk
import no.nav.sokos.skattekort.module.bestilling.svar.Root
import no.nav.sokos.skattekort.module.bestilling.svar.Skattekort

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

class SkattekortFixedRecordFormatterDuplicatorTest :
    FunSpec({
        test("gå gjennom alle skattekort og sjekk at vi får et stabilt svar") {
            val content = readFile("/oppdragz/skattekortsvar.json")
            val dataObject = Json.decodeFromString<Root>(content)
            val arbeidstakere: List<Arbeidstaker> = dataObject.arbeidsgiver.map { it.arbeidstaker }.flatten()
            val referanseverdier: Map<String, String> =
                arbeidstakere
                    .map { arbeidstaker ->
                        val skattekortmelding = arbeidstakerConverter(arbeidstaker)
                        val nyFormatering = SkattekortFixedRecordFormatter(skattekortmelding, "2025").format()
                        val gammelFormatering = GammelFormatter(skattekortmelding, "2025").format()
                        assertEquals(nyFormatering, gammelFormatering)
                        Pair(arbeidstaker.arbeidstakeridentifikator, nyFormatering)
                    }.toMap()
            File("src/test/resources/oppdragz/skattekortreferanser.json").writeText(Json { prettyPrint = true }.encodeToString(referanseverdier))
        }
    })
