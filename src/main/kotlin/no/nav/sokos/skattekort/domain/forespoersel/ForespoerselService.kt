package no.nav.sokos.skattekort.domain.forespoersel

import kotlin.time.ExperimentalTime

import com.zaxxer.hikari.HikariDataSource
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.person.PersonService
import no.nav.sokos.skattekort.domain.person.Personidentifikator
import no.nav.sokos.skattekort.domain.skattekort.Bestilling
import no.nav.sokos.skattekort.domain.skattekort.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val FORESPOERSEL_DELIMITER = ";"
private const val FORSYSTEM = "FORSYSTEM"
private const val INNTEKTSAAR = "INNTEKTSAAR"
private const val FNR = "FNR"

class ForespoerselService(
    private val dataSource: HikariDataSource,
    private val personService: PersonService,
) {
    @OptIn(ExperimentalTime::class)
    fun taImotForespoersel(message: String) {
        dataSource.transaction { session ->
            val forespoerselMap = parseForespoersel(message)
            val person =
                personService.findOrCreatePersonByFnr(
                    fnr = forespoerselMap[FNR] as Personidentifikator,
                    informasjon = "Mottatt forespørsel på skattekort",
                )

            val forespoerselId =
                ForespoerselRepository.insert(
                    tx = session,
                    forsystem = forespoerselMap[FORSYSTEM] as Forsystem,
                    dataMottatt = message,
                )

            val abonnementId: List<Long?> =
                AbonnementRepository.insertBatchAndReturnKeys(
                    tx = session,
                    forespoerselId = forespoerselId,
                    inntektsaar = forespoerselMap[INNTEKTSAAR] as Int,
                    personListe = listOf(person),
                )

            val fnr = (forespoerselMap[FNR] as Personidentifikator).value
            val forsystem = (forespoerselMap[FORSYSTEM] as Forsystem).kode
            val inntektsaar = forespoerselMap[INNTEKTSAAR] as Int

            println("forespoerselMap = $forespoerselMap")

            // TODO bruke insertBatch
            // Unngå duplikater? Eller la det være flere hvis forsystemene skulle ha bestilt flere ganger?
            // Hvis vi beholder alle, kan vi se om et forsystem har bestilt flere ganger og kunne oppdage feilsituasjon
            // Hvis vi sørger for at det bare er en, så skjuler vi den informasjonen.
            // Det er trivielt å slette alle duplikate utsendinger når vi gjør en utsending.
            // Flyttes inn i Service/Repoklasse når denne finnes.
            session.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    |INSERT INTO utsendinger (
                    |abonnement_id,
                    |fnr,
                    |forsystem,
                    |inntektsaar
                    |)
                    |VALUES (:abonnement_id, :fnr, :forsystem, :inntektsaar)
                    """.trimMargin(),
                    mapOf(
                        "abonnement_id" to abonnementId.first(),
                        "fnr" to (forespoerselMap[FNR] as Personidentifikator).value,
                        "forsystem" to (forespoerselMap[FORSYSTEM] as Forsystem).kode,
                        "inntektsaar" to forespoerselMap[INNTEKTSAAR],
                    ),
                ),
            )

            // Opprett bestilling for et subsett av personer i forespørselen som vi
            // ikke har skattekort for fra før
            // OG ikke har bestilling på fra før.
            BestillingRepository.insert(
                tx = session,
                bestilling =
                    Bestilling(
                        personId = person.id!!,
                        fnr = person.foedselsnummer.fnr,
                        inntektsaar = forespoerselMap[INNTEKTSAAR] as Int,
                    ),
            )
        }
    }

    private fun parseForespoersel(message: String): Map<String, Any> {
        val parts = message.split(FORESPOERSEL_DELIMITER)
        require(parts.size == 3) { "Invalid message format: $message" }
        val forsystem = Forsystem.fromValue(parts[0])
        val inntektsaar = Integer.parseInt(parts[1])
        val fnrString = parts[2]

        return mapOf(
            FORSYSTEM to forsystem,
            INNTEKTSAAR to inntektsaar,
            FNR to Personidentifikator(fnrString),
        )
    }
}
