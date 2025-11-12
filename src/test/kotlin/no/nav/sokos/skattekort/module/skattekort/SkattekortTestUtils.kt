package no.nav.sokos.skattekort.module.skattekort

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths

import kotlinx.serialization.json.Json

import kotliquery.TransactionalSession

import no.nav.sokos.skattekort.TestUtil.runThisSql
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Arbeidstaker
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.Skattekort
import no.nav.sokos.skattekort.util.SQLUtils.transaction

fun aForskuddstrekk(
    type: String,
    trekkode: Trekkode,
    prosentSats: Double,
    antMndForTrekk: Double? = null,
    tabellNummer: String? = null,
): Forskuddstrekk =
    when (type) {
        Prosentkort::class.simpleName ->
            Prosentkort(
                trekkode.value,
                BigDecimal(prosentSats).setScale(2, RoundingMode.HALF_UP),
                antMndForTrekk?.let { belop -> BigDecimal(belop).setScale(1, RoundingMode.HALF_UP) },
            )

        Tabellkort::class.simpleName ->
            Tabellkort(
                trekkode.value,
                tabellNummer!!,
                BigDecimal(prosentSats).setScale(2, RoundingMode.HALF_UP),
                BigDecimal(antMndForTrekk ?: 12.0).setScale(1, RoundingMode.HALF_UP),
            )

        else -> error("Ukjent forskuddstrekk-type: $type")
    }

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
        tilleggsopplysning = tilleggsopplysninger?.map { it.opplysning },
        inntektsaar = inntektsaar,
    )

fun aHentSkattekortResponse(vararg arbeidstakere: Arbeidstaker): HentSkattekortResponse =
    HentSkattekortResponse(
        status = "FORESPOERSEL_OK",
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
) = """
        INSERT INTO bestillingsbatcher (id, bestillingsreferanse, data_sendt, status)
            VALUES ($id, '$ref', '{}', '$status');
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

fun <T> tx(block: (TransactionalSession) -> T): T = DbListener.dataSource.transaction { tx -> block(tx) }

fun toBestillSkattekortResponse(json: String) =
    Json.decodeFromString(
        no.nav.sokos.skattekort.skatteetaten.bestillskattekort.BestillSkattekortResponse
            .serializer(),
        json,
    )
