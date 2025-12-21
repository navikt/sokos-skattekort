package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.serialization.json.Json

import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Skattekort
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Trekkprosent
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Trekktabell
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
): no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk =
    no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk(
        trekkode = trekkode.value,
        trekktabell = tabellNummer?.let { Trekktabell(it, BigDecimal(trekkprosent!!).setScale(2, RoundingMode.HALF_UP), BigDecimal(12).setScale(1, RoundingMode.HALF_UP)) },
        trekkprosent = trekkprosent?.let { Trekkprosent(BigDecimal(it).setScale(2, RoundingMode.HALF_UP), null) },
        frikort =
            frikortbeloep?.let {
                no.nav.sokos.skattekort.skatteetaten.hentskattekort
                    .Frikort(BigDecimal(frikortbeloep).setScale(2, RoundingMode.HALF_UP))
            },
    )

fun aSkattekort(
    utstedtDato: String,
    identifikator: Long,
    forskuddstrekk: List<no.nav.sokos.skattekort.skatteetaten.hentskattekort.Forskuddstrekk>,
): Skattekort =
    Skattekort(
        utstedtDato = utstedtDato,
        skattekortidentifikator = identifikator,
        forskuddstrekk = forskuddstrekk,
    )

fun anArbeidstaker(
    resultat: ResultatForSkattekort,
    fnr: String,
    inntektsaar: String,
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
                no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidsgiver(
                    arbeidsgiveridentifikator =
                        no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidsgiveridentifikator(
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

fun aPerson(
    personId: Long,
    fnr: String,
) = """
        INSERT INTO personer(id) VALUES ($personId);
            
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
                    VALUES ($forespoerselId, '', '${forsystem.value}');
    
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

fun toBestillSkattekortResponse(json: String) =
    Json.decodeFromString(
        no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
            .serializer(),
        json,
    )
