package no.nav.sokos.skattekort.domain.forespoersel

import kotlin.time.ExperimentalTime

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.domain.person.PersonService
import no.nav.sokos.skattekort.domain.person.Personidentifikator
import no.nav.sokos.skattekort.domain.skattekort.Bestilling
import no.nav.sokos.skattekort.domain.skattekort.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val FORESPOERSEL_DELIMITER = ";"

class ForespoerselService(
    private val dataSource: HikariDataSource,
    private val personService: PersonService,
) {
    @OptIn(ExperimentalTime::class)
    fun taImotForespoersel(message: String) {
        dataSource.transaction { session ->
            val abonnementList = parseForespoersel(message)
            val abonnement = abonnementList.first()
            val person =
                personService.findOrCreatePersonByFnr(
                    fnr = abonnement.person.foedselsnummer.fnr,
                    informasjon = "Mottatt forespørsel på skattekort",
                )

            val forespoerselId =
                ForespoerselRepository.insert(
                    tx = session,
                    forsystem = abonnement.forespoersel.forsystem,
                    dataMottatt = message,
                )

            val abonnementIdList: List<Int> =
                AbonnementRepository.insertBatch(
                    tx = session,
                    forespoerselId = forespoerselId,
                    inntektsaar = abonnement.inntektsaar,
                    personListe = listOf(person),
                )

            // Flyttes inn i Service/Repoklasse når denne finnes.
            session.batchPreparedNamedStatement(
                """
                    |INSERT INTO utsendinger (
                    |abonnement_id,
                    |fnr,
                    |forsystem,
                    |inntektsaar
                    |)
                    |VALUES (:abonnement_id, :fnr, :forsystem, :inntektsaar)
                """.trimMargin(),
                abonnementIdList.map { abonnementId ->
                    mapOf(
                        "abonnement_id" to abonnementId,
                        "fnr" to abonnement.person.foedselsnummer.fnr.value,
                        "forsystem" to abonnement.forespoersel.forsystem.kode,
                        "inntektsaar" to abonnement.inntektsaar,
                    )
                },
            )

            // Vi sier det er greit at vi har duplikate bestillinger
            BestillingRepository.insert(
                tx = session,
                bestilling =
                    Bestilling(
                        personId = person.id!!,
                        fnr = person.foedselsnummer.fnr,
                        inntektsaar = abonnement.inntektsaar,
                    ),
            )
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun parseForespoersel(message: String): List<Abonnement> {
        val parts = message.split(FORESPOERSEL_DELIMITER)
        require(parts.size == 3) { "Invalid message format: $message" }
        val forsystem = Forsystem.fromValue(parts[0])
        val inntektsaar = Integer.parseInt(parts[1])
        val fnrString = parts[2]
        val forespoersel = Forespoersel(forsystem = forsystem, dataMottatt = message)
        return listOf(
            Abonnement(
                forespoersel = forespoersel,
                inntektsaar = inntektsaar,
                person =
                    personService.findOrCreatePersonByFnr(
                        fnr = Personidentifikator(fnrString),
                        informasjon = "Mottatt forespørsel på skattekort",
                    ),
            ),
        )
    }
}
