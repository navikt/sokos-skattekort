package no.nav.sokos.skattekort.module.forespoersel

import java.sql.BatchUpdateException
import java.time.LocalDate
import java.time.LocalDateTime
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Month
import kotlinx.datetime.toKotlinLocalDateTime

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val DELIMITER = ";"
private val logger = KotlinLogging.logger { }

class ForespoerselService(
    private val dataSource: DataSource,
    private val personService: PersonService,
    private val featureToggles: UnleashIntegration,
) {
    fun taImotForespoersel(
        message: String,
        saksbehandler: Saksbehandler? = null,
    ) {
        runCatching {
            logger.info(marker = TEAM_LOGS_MARKER) { "Motta forespørsel på skattekort: $message" }

            val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
            val forespoerselInput =
                when {
                    message.startsWith("<") -> return
                    else -> parseCopybookMessage(message)
                }.let { input ->
                    input.copy(
                        fnrList =
                            input.fnrList.filter { fnr ->
                                val erGyldig = foedselsnummerkategori.erGyldig(fnr)
                                if (!erGyldig) {
                                    logger.info(marker = TEAM_LOGS_MARKER) { "fjernet ugyldig fnr fra kall: $fnr" }
                                }
                                erGyldig
                            },
                    )
                }

            dataSource.transaction { tx ->
                handleForespoersel(tx, message, forespoerselInput, saksbehandler?.ident)
                if (skalLagesForNesteAarOgsaa(forespoerselInput)) {
                    val forespoerselForNesteAar = forespoerselInput.copy(inntektsaar = forespoerselInput.inntektsaar + 1)
                    handleForespoersel(tx, message, forespoerselForNesteAar, saksbehandler?.ident)
                }
            }
        }.onFailure { exception ->
            logger.error { "Feil ved mottak av forespørsel på skattekort, sjekk feilmeldingen i team logs." }
            logger.error(marker = TEAM_LOGS_MARKER, exception) { "Feil ved mottak av forespørsel på skattekort: $message" }
            throw exception
        }
    }

    private fun skalLagesForNesteAarOgsaa(forespoerselInput: ForespoerselInput): Boolean {
        val now = LocalDateTime.now().toKotlinLocalDateTime()
        val thisYear = now.year
        return (forespoerselInput.inntektsaar == thisYear && now.month == Month.DECEMBER && now.day >= 15)
    }

    @OptIn(ExperimentalTime::class)
    private fun handleForespoersel(
        tx: TransactionalSession,
        message: String,
        forespoerselInput: ForespoerselInput,
        brukerId: String?,
    ) {
        val forespoerselId =
            ForespoerselRepository.insert(
                tx = tx,
                forsystem = forespoerselInput.forsystem,
                dataMottatt = message,
            )

        var bestillingCount = 0
        var utsendingCount = 0

        forespoerselInput.fnrList.forEach { fnr ->
            val personId =
                personService
                    .findPersonIdOrCreatePersonByFnr(
                        tx = tx,
                        fnr = Personidentifikator(fnr),
                        informasjon = "Mottatt forespørsel: $forespoerselId, forsystem: ${forespoerselInput.forsystem.name} på skattekort",
                        brukerId = brukerId,
                    ).first

            AbonnementRepository.insert(
                tx = tx,
                forespoerselId = forespoerselId,
                inntektsaar = forespoerselInput.inntektsaar,
                personId = personId.value,
            ) ?: throw IllegalStateException("Kunne ikke lage abonnement")

            val skattekort =
                SkattekortRepository
                    .findAllByPersonId(tx, personId, forespoerselInput.inntektsaar, adminRole = false)

            if (skattekort.isEmpty()) {
                val forSentAaBestille = forSentAaBestille(forespoerselInput.inntektsaar)
                if (forSentAaBestille) logger.warn { "Vi kan ikke lenger bestille skattekort for ${forespoerselInput.inntektsaar}" }
                if (!forSentAaBestille && BestillingRepository.findByPersonIdAndInntektsaar(tx, personId, forespoerselInput.inntektsaar) == null) {
                    BestillingRepository.insert(
                        tx = tx,
                        bestilling =
                            Bestilling(
                                personId = personId,
                                fnr = Personidentifikator(fnr),
                                inntektsaar = forespoerselInput.inntektsaar,
                            ),
                    )
                    bestillingCount++
                }
            } else {
                // Skattekort finnes
                val utsending = UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), forespoerselInput.inntektsaar, forespoerselInput.forsystem)
                if (utsending != null) {
                    logger.info {
                        "Utsending eksisterer allerede for personId: ${personId.value}, inntektsår: ${forespoerselInput.inntektsaar}, forsystem: ${forespoerselInput.forsystem.name} hopper over opprettelse av utsending"
                    }
                } else {
                    UtsendingRepository.insert(tx, Utsending(null, Personidentifikator(fnr), forespoerselInput.inntektsaar, forespoerselInput.forsystem))
                    utsendingCount++
                }
            }
        }

        logger.info {
            "ForespoerselId: $forespoerselId med total: ${forespoerselInput.fnrList.size} abonnement(er), $bestillingCount bestilling(er), $utsendingCount utsending(er) for inntektsår: ${forespoerselInput.inntektsaar}"
        }
    }

    private fun forSentAaBestille(inntektsaar: Int): Boolean {
        // Skatteetatens regel er at man kan bestille skattekort for året før frem til 01.07.
        val currentDate = LocalDate.now()
        val currentYear = currentDate.year
        val cutoffDate = LocalDate.of(currentYear, 7, 1)
        return currentDate.isAfter(cutoffDate) && inntektsaar == currentYear - 1
    }

    @OptIn(ExperimentalTime::class)
    private fun parseCopybookMessage(message: String): ForespoerselInput {
        val parts = message.split(DELIMITER).filter { it.isNotBlank() }
        val forsystem =
            when {
                Forsystem.OPPDRAGSSYSTEMET == Forsystem.fromValue(parts[0]) && parts.size > 3 -> Forsystem.OPPDRAGSSYSTEMET_STOR
                else -> Forsystem.fromValue(parts[0])
            }
        val inntektsaar = Integer.parseInt(parts[1])

        return ForespoerselInput(
            forsystem = forsystem,
            inntektsaar = inntektsaar,
            fnrList = parts.drop(2).map { it },
        )
    }

    fun cronForespoerselInput() {
        if (featureToggles.isForespoerselInputEnabled()) {
            val forespoerselInput: List<ForespoerselInput> =
                dataSource.transaction { tx ->
                    val returverdi = ForespoerselRepository.getAllForespoerselInput(tx)
                    ForespoerselRepository.deleteAllForespoerselInput(tx)
                    returverdi
                }
            forespoerselInput.forEach { input ->
                var i = 0
                retry@ while (i < 5) {
                    try {
                        dataSource.transaction { tx ->
                            val message = "${input.forsystem};${input.inntektsaar};${input.fnrList.first()}"
                            handleForespoersel(tx, message, input, null)
                        }
                        break@retry
                    } catch (e: BatchUpdateException) {
                        logger.error(marker = TEAM_LOGS_MARKER, e) { "Exception under håndtering av forespoersel fra database: ${e.message}" }
                        logger.error("Exception under håndtering av forespoersel fra database, detaljer er logget til secure log")
                        i++
                    } catch (e: Exception) {
                        logger.error("Exception under håndtering av forespoersel fra database: ${e.message}", e)
                        i++
                    }
                }
            }
        }
    }

    data class ForespoerselInput(
        val forsystem: Forsystem,
        val inntektsaar: Int,
        val fnrList: List<String>,
    )
}
