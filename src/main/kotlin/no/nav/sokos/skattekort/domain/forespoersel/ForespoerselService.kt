package no.nav.sokos.skattekort.domain.forespoersel

import kotlin.time.ExperimentalTime

import com.zaxxer.hikari.HikariDataSource

import no.nav.sokos.skattekort.domain.person.PersonService
import no.nav.sokos.skattekort.domain.person.Personidentifikator
import no.nav.sokos.skattekort.domain.skattekort.Bestilling
import no.nav.sokos.skattekort.domain.skattekort.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val FORESPOERSEL_DELIMITER = ";"
private const val FORSYSTEM = "SKATTEKORT"
private const val AAR = "AAR"
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

            print("Forespørsel mottatt: $message for personId=${person.id}")
            val forespoerselId =
                ForespoerselRepository.insert(
                    tx = session,
                    forsystem = forespoerselMap[FORSYSTEM] as Forsystem,
                    dataMottatt = message,
                )
            print("Lagret forespørsel med id=$forespoerselId")
            AbonnementRepository.insertBatch(
                tx = session,
                forespoerselId = forespoerselId,
                aar = forespoerselMap[AAR] as Int,
                personListe = listOf(person),
            )

            BestillingRepository.insert(
                tx = session,
                bestilling =
                    Bestilling(
                        personId = person.id!!,
                        fnr = person.foedselsnummer.fnr,
                        aar = forespoerselMap[AAR] as Int,
                    ),
            )
        }
    }

    private fun parseForespoersel(message: String): Map<String, Any> {
        val parts = message.split(FORESPOERSEL_DELIMITER)
        require(parts.size == 3) { "Invalid message format: $message" }
        val forsystem = Forsystem.fromValue(parts[0])
        val aar = Integer.parseInt(parts[1])
        val fnrString = parts[2]

        return mapOf(
            FORSYSTEM to forsystem,
            AAR to aar,
            FNR to Personidentifikator(fnrString),
        )
    }
}
