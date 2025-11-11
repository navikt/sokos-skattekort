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
import no.nav.sokos.skattekort.skatteetaten.hentskattekort.HentSkattekortResponse
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

fun aHentSkattekortResponse(
    resultat: ResultatForSkattekort,
    fnr: String,
    inntektsaar: String,
    tilleggsopplysninger: List<Tilleggsopplysning>? = null,
): HentSkattekortResponse {
    val tilleggsopplysningerString =
        if (tilleggsopplysninger.isNullOrEmpty()) {
            "null"
        } else {
            "[${tilleggsopplysninger.joinToString(prefix = "\"", postfix = "\"", transform = { it.opplysning })}],"
        }
    return when (resultat) {
        ResultatForSkattekort.IkkeSkattekort, ResultatForSkattekort.UgyldigFoedselsEllerDnummer ->
            Json.decodeFromString(
                HentSkattekortResponse.serializer(),
                """
                {
                  "status": "FORESPOERSEL_OK",
                  "arbeidsgiver": [
                    {
                      "arbeidsgiveridentifikator": {
                        "organisasjonsnummer": "312978083"
                      },
                      "arbeidstaker": [
                        {
                          "arbeidstakeridentifikator": "$fnr",
                          "resultatForSkattekort": "${resultat.value}",
                          "tilleggsopplysning": $tilleggsopplysningerString
                          "inntektsaar": "$inntektsaar"
                        }
                      ]
                    }
                  ]
                }
                """.trimIndent(),
            )

        else -> error("Et fiktivt skattekort med resultatForSkattekort $resultat er ikke implementert i testutil")
    }
}

fun aHentSkattekortResponseFromFile(jsonfile: String): HentSkattekortResponse = Json.decodeFromString(HentSkattekortResponse.serializer(), Files.readString(Paths.get("src/test/resources/$jsonfile")))

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
    batchId: Long,
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
