package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.serialization.json.Json

import no.nav.sokos.skattekort.infrastructure.skatteetaten.bestillskattekort.BestillSkattekortResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiver
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidsgiveridentifikator
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Skattekort
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Trekkprosent
import no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Trekktabell
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.skattekort.Forskuddstrekk
import no.nav.sokos.skattekort.skattekort.Frikort
import no.nav.sokos.skattekort.skattekort.Prosentkort
import no.nav.sokos.skattekort.skattekort.ResponseStatus
import no.nav.sokos.skattekort.skattekort.ResultatForSkattekort
import no.nav.sokos.skattekort.skattekort.Tabellkort
import no.nav.sokos.skattekort.skattekort.Tilleggsopplysning
import no.nav.sokos.skattekort.skattekort.Trekkode
import no.nav.sokos.skattekort.utils.TestUtils.runThisSql

fun aForskuddstrekk(
    type: String,
    trekkode: Trekkode,
    prosentSats: Double? = null,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
    frikortbeløp: Int? = null,
): Forskuddstrekk =
    when (type) {
        Prosentkort::class.simpleName -> {
            Prosentkort(
                trekkode,
                BigDecimal(prosentSats!!).setScale(2, RoundingMode.HALF_UP),
                antMndForTrekk?.let { belop -> BigDecimal(belop).setScale(1, RoundingMode.HALF_UP) },
            )
        }

        Tabellkort::class.simpleName -> {
            Tabellkort(
                trekkode,
                tabellNummer!!,
                BigDecimal(prosentSats!!).setScale(2, RoundingMode.HALF_UP),
                BigDecimal(antMndForTrekk ?: 12.0).setScale(1, RoundingMode.HALF_UP),
            )
        }

        Frikort::class.simpleName -> {
            Frikort(
                trekkode,
                frikortbeløp,
            )
        }

        else -> {
            error("Ukjent forskuddstrekk-type: $type")
        }
    }

fun aSkdForskuddstrekk(
    trekkode: Trekkode,
    trekkprosent: Double? = null,
    tabellNummer: String? = null,
    frikortbeloep: Int? = null,
): no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort.Forskuddstrekk =
    Forskuddstrekk(
        trekkode = trekkode.value,
        trekktabell = tabellNummer?.let { Trekktabell(it, BigDecimal(trekkprosent!!).setScale(2, RoundingMode.HALF_UP), BigDecimal(12).setScale(1, RoundingMode.HALF_UP)) },
        trekkprosent = trekkprosent?.let { Trekkprosent(BigDecimal(it).setScale(2, RoundingMode.HALF_UP), null) },
        frikort =
            frikortbeloep?.let {
                no.nav.sokos.skattekort.infrastructure.skatteetaten.hentskattekort
                    .Frikort(BigDecimal(frikortbeloep).setScale(2, RoundingMode.HALF_UP))
            },
    )

fun aSkattekort(
    utstedtDato: String,
    identifikator: Long,
    forskuddstrekk: List<Forskuddstrekk>,
): Skattekort =
    Skattekort(
        utstedtDato = utstedtDato,
        skattekortidentifikator = identifikator,
        forskuddstrekk = forskuddstrekk,
    )

fun anArbeidstaker(
    resultat: ResultatForSkattekort,
    fnr: String,
    inntektsaar: Int,
    tilleggsopplysninger: List<Tilleggsopplysning>? = null,
    skattekort: Skattekort? = null,
): Arbeidstaker =
    Arbeidstaker(
        arbeidstakeridentifikator = fnr,
        resultatForSkattekort = resultat.value,
        skattekort = skattekort,
        tilleggsopplysning = tilleggsopplysninger?.map { it.value },
        inntektsaar = inntektsaar,
    )

fun aHentSkattekortResponse(
    vararg arbeidstakere: Arbeidstaker,
    response: ResponseStatus = ResponseStatus.FORESPOERSEL_OK,
): HentSkattekortResponse =
    HentSkattekortResponse(
        status = response.name,
        arbeidsgiver =
            listOf(
                Arbeidsgiver(
                    arbeidsgiveridentifikator =
                        Arbeidsgiveridentifikator(
                            organisasjonsnummer = "312978083",
                        ),
                    arbeidstaker = arbeidstakere.toList(),
                ),
            ),
    )

fun aHentSkattekortResponseFromFile(jsonfile: String): HentSkattekortResponse = Json.decodeFromString(HentSkattekortResponse.serializer(), Files.readString(Paths.get(jsonfile)))

fun databaseHas(vararg strings: String) {
    runThisSql(strings.joinToString("\n"))
}

fun aPerson(personId: Long) =
    """
        INSERT INTO personer(id) VALUES ($personId);            
    """

fun afoedselsnummer(
    personId: Long,
    fnr: String,
) = """
        INSERT INTO foedselsnumre(person_id, fnr)
            VALUES ($personId, '$fnr');
    """

fun aBestillingsBatch(
    id: Long,
    ref: String,
    status: String,
    type: String = "BESTILLING",
) = """
        INSERT INTO bestillingsbatcher (id, bestillingsreferanse, data_sendt, status, type)
            VALUES ($id, '$ref', '{}', '$status', '$type');
    """

fun aBestilling(
    personId: Long,
    fnr: String,
    inntektsaar: Int,
    batchId: Long?,
) = """
    INSERT INTO bestillinger(person_id, fnr, inntektsaar, bestillingsbatch_id)
                    VALUES ($personId, '$fnr', $inntektsaar, $batchId);
    """.trimIndent()

fun anAbonnement(
    forespoerselId: Long,
    personId: Long,
    inntektsaar: Int,
    forsystem: Forsystem = Forsystem.OPPDRAGSSYSTEMET,
) = """
    INSERT INTO forespoersler(id, data_mottatt, forsystem)
        SELECT $forespoerselId, '${forsystem.value}:$inntektsaar:' || fnr, '${forsystem.value}' FROM foedselsnumre WHERE person_id = $personId;
    
    INSERT INTO abonnementer(forespoersel_id, person_id, inntektsaar)
                    VALUES ($forespoerselId, $personId, $inntektsaar);
    """.trimIndent()

fun aDbSkattekort(
    id: Long,
    personId: Long,
    utstedtDato: String,
    identifikator: String,
    inntektsaar: Int,
    opprettet: String,
    kilde: String = "skatteetaten",
    resultatForSkattekort: ResultatForSkattekort = ResultatForSkattekort.SkattekortopplysningerOK,
    generertFra: Long? = null,
) = """
    INSERT INTO skattekort (id, person_id, utstedt_dato, identifikator, inntektsaar, kilde, opprettet, resultatForSkattekort, generert_fra)
    VALUES ($id, $personId, '$utstedtDato', '$identifikator', $inntektsaar, '$kilde', '$opprettet', '${resultatForSkattekort.value}', $generertFra);
    """.trimIndent()

fun aDbForskuddstrekk(
    id: Long,
    skattekortId: Long,
    type: String,
    trekkode: Trekkode,
    prosentSats: Double? = null,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
    frikortbeløp: Int? = null,
) = """
    INSERT INTO forskuddstrekk (id, skattekort_id, type, trekk_kode, prosentsats, antall_mnd_for_trekk, tabell_nummer, frikort_beloep)
    VALUES ($id, $skattekortId, '$type', '${trekkode.value}', ${prosentSats ?: "NULL"}, ${antMndForTrekk ?: "NULL"}, ${tabellNummer?.let { "'$it'" } ?: "NULL"}, ${frikortbeløp ?: "NULL"});
    """.trimIndent()

fun toBestillSkattekortResponse(json: String) =
    Json.decodeFromString(
        BestillSkattekortResponse
            .serializer(),
        json,
    )

fun anUtsending(
    fnr: String,
    inntektsaar: Int,
    forsystem: String,
) = """
    INSERT INTO utsendinger (fnr, inntektsaar, forsystem)
    VALUES ('$fnr', $inntektsaar, '$forsystem');
    """.trimIndent()
