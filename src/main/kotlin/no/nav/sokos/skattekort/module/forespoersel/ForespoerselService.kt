package no.nav.sokos.skattekort.module.forespoersel

import javax.sql.DataSource

import kotlin.time.ExperimentalTime

import kotliquery.TransactionalSession
import mu.KotlinLogging
import tools.jackson.module.kotlin.readValue

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.config.xmlMapper
import no.nav.sokos.skattekort.module.forespoersel.arena.Applikasjon
import no.nav.sokos.skattekort.module.forespoersel.arena.ESkattekortBestilling
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.security.NavIdent
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val FORESPOERSEL_DELIMITER = ";"
private val logger = KotlinLogging.logger { }

class ForespoerselService(
    private val dataSource: DataSource,
    private val personService: PersonService,
) {
    fun taImotForespoersel(
        message: String,
        saksbehandler: NavIdent? = null,
    ) {
        dataSource.transaction { tx ->
            val forespoerselInput =
                when {
                    message.startsWith("<") -> parseXmlMessage(message)
                    else -> parseCopybookMessage(message)
                }

            logger.info(marker = TEAM_LOGS_MARKER) { "Motta forespørsel på skattekort: $forespoerselInput" }

            val forespoerselId =
                ForespoerselRepository.insert(
                    tx = tx,
                    forsystem = forespoerselInput.forsystem,
                    dataMottatt = message,
                )
            createBestilling(tx, forespoerselId, forespoerselInput, saksbehandler?.ident)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun createBestilling(
        tx: TransactionalSession,
        forespoerselId: Long,
        forespoerselInput: ForespoerselInput,
        brukerId: String?,
    ) {
        var bestillingCount = 0
        if (forespoerselInput.inntektsaar < 2025) {
            logger.warn {
                "ForespoerselId: $forespoerselId har inntektsår ${forespoerselInput.inntektsaar} og ignoreres "
            }
            return
        }
        forespoerselInput.fnrList.forEach { fnr ->
            val person =
                personService.findOrCreatePersonByFnr(
                    tx = tx,
                    fnr = Personidentifikator(fnr),
                    informasjon = "Mottatt forespørsel: $forespoerselId, forsystem: ${forespoerselInput.forsystem.name} på skattekort",
                    brukerId = brukerId,
                )

            UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), forespoerselInput.inntektsaar, forespoerselInput.forsystem)?.let {
                logger.info {
                    "Utsending eksisterer allerede for personId: ${person.id}, inntektsår: ${forespoerselInput.inntektsaar}, forsystem: ${forespoerselInput.forsystem.name} hopper over opprettelse av abonnement og utsending"
                }
                return@forEach
            }

            val abonnementId =
                AbonnementRepository.insert(
                    tx = tx,
                    forespoerselId = forespoerselId,
                    inntektsaar = forespoerselInput.inntektsaar,
                    personId = person.id!!.value,
                )

            SkattekortRepository.findAllByPersonId(tx, person.id, forespoerselInput.inntektsaar).ifEmpty {
                if (BestillingRepository.findByPersonIdAndInntektsaar(tx, person.id, forespoerselInput.inntektsaar) == null) {
                    BestillingRepository.insert(
                        tx = tx,
                        bestilling =
                            Bestilling(
                                personId = person.id,
                                fnr = Personidentifikator(fnr),
                                inntektsaar = forespoerselInput.inntektsaar,
                            ),
                    )
                    bestillingCount++
                }
            }
        }
        logger.info { "ForespoerselId: $forespoerselId med total: ${forespoerselInput.fnrList.size} abonnement(er), $bestillingCount bestilling(er)" }
    }

    @OptIn(ExperimentalTime::class)
    private fun parseXmlMessage(message: String): ForespoerselInput {
        val eSkattekortBestilling = xmlMapper.readValue<ESkattekortBestilling>(message)
        val forsystem =
            when (eSkattekortBestilling.bestiller) {
                Applikasjon.ARENA -> Forsystem.ARENA
                Applikasjon.OS -> Forsystem.OPPDRAGSSYSTEMET
            }

        return ForespoerselInput(
            forsystem = forsystem,
            inntektsaar = eSkattekortBestilling.inntektsaar.toInt(),
            fnrList = eSkattekortBestilling.brukere,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun parseCopybookMessage(message: String): ForespoerselInput {
        val parts = message.split(FORESPOERSEL_DELIMITER)
        require(parts.size == 3) { "Invalid message format: $message" }
        val forsystem = Forsystem.fromValue(parts[0])
        val inntektsaar = Integer.parseInt(parts[1])
        val fnrString = parts[2]

        return ForespoerselInput(
            forsystem = forsystem,
            inntektsaar = inntektsaar,
            fnrList = listOf(fnrString),
        )
    }

    private data class ForespoerselInput(
        val forsystem: Forsystem,
        val inntektsaar: Int,
        val fnrList: List<String>,
    )
}
