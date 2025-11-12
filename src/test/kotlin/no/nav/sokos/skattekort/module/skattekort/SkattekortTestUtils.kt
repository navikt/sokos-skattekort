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

fun aTabellkort(
    trekkode: Trekkode,
    tabellNummer: String,
    prosentSats: Int,
    antMndForTrekk: Double? = null,
): String =
    """ 
    {
        "trekkode": "${trekkode.value}",
        "trekktabell": {
            "tabellnummer": "$tabellNummer",
            "prosentsats": $prosentSats,
            "antallMaanederForTrekk": $antMndForTrekk
        }
    }
    """.trimIndent()

fun aProsentkort(
    trekkode: Trekkode,
    prosentSats: Int,
) = """
    {
      "trekkode": "${trekkode.value}",
      "trekkprosent": {
        "prosentsats": "$prosentSats"
      }
    }
    """.trimIndent()

fun aSkattekort(
    utstedtDato: String,
    identifikator: Long,
    forskuddstrekk: List<String>,
) = """
    "skattekort": {
      "utstedtDato": "$utstedtDato",
      "skattekortidentifikator": $identifikator,
      "forskuddstrekk": [
        ${forskuddstrekk.joinToString(",")}
      ]
    },
    """.trimIndent()

fun anArbeidstaker(
    resultat: ResultatForSkattekort,
    fnr: String,
    inntektsaar: String,
    tilleggsopplysninger: List<Tilleggsopplysning>? = null,
    skattekort: String? = "",
): String {
    val tilleggsopplysningerString =
        if (tilleggsopplysninger.isNullOrEmpty()) {
            "null"
        } else {
            "[${tilleggsopplysninger.joinToString(transform = { "\"${it.opplysning}\"" })}],"
        }
    return """
            {
            "arbeidstakeridentifikator": "$fnr",
            "resultatForSkattekort": "${resultat.value}",
            $skattekort
            "tilleggsopplysning": $tilleggsopplysningerString
            "inntektsaar": "$inntektsaar"
        }
        """.trimIndent()
}

fun aHentSkattekortResponse(vararg arbeidstakere: String): HentSkattekortResponse =
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
                ${arbeidstakere.joinToString(",")}
              ]
            }
          ]
        }
        """.trimIndent(),
    )

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
