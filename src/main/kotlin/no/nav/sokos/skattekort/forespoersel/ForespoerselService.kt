package no.nav.sokos.skattekort.forespoersel

import java.time.LocalDate
import javax.sql.DataSource

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.person.AuditRepository
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.Personidentifikator
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.skattekort.ReglerForInntektsaar
import no.nav.sokos.skattekort.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.skattekorthenting.Bestilling
import no.nav.sokos.skattekort.skattekorthenting.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utsending.Utsending
import no.nav.sokos.skattekort.utsending.UtsendingRepository

private const val DELIMITER = ";"
private const val CHUNKED_SIZE = 1000
private val logger = KotlinLogging.logger { }

class ForespoerselService(
    private val dataSource: DataSource,
    private val personService: PersonService,
    private val featureToggles: UnleashIntegration,
) {
    private typealias BestillingCount = Int
    private typealias UtsendingCount = Int

    fun taImotForespoersel(
        message: String,
        saksbehandler: Saksbehandler? = null,
    ) {
        runCatching {
            logger.info(marker = TEAM_LOGS_MARKER) { "Motta forespørsel på skattekort: $message" }

            val forespoerselInput =
                when {
                    message.startsWith("<") -> return
                    else -> parseCopybookMessage(message)
                }.let { input ->
                    input.copy(fnrList = personService.validateFoedselsnummer(input.fnrList))
                }

            val forSentAaBestille = forSentAaBestille(forespoerselInput.inntektsaar)
            if (forSentAaBestille) {
                logger.warn { "Vi kan ikke lenger bestille skattekort for ${forespoerselInput.inntektsaar}" }
                return
            }

            if (forespoerselInput.fnrList.isEmpty()) {
                logger.error { "Ingen data blir lagret i forespørseler pga. ugyldig fnr" }
                return
            }

            val foedselsnumreWithPersonIdMap = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(forespoerselInput.fnrList, saksbehandler?.ident)
            dataSource.transaction { tx ->
                handleForespoersel(tx, message, forespoerselInput, foedselsnumreWithPersonIdMap, saksbehandler?.ident)
                if (forespoerselInput.forsystem == Forsystem.OPPDRAGSSYSTEMET_STOR) return@transaction

                val skalLagesForNesteAarOgsaa = ReglerForInntektsaar.lovligeInntektsAarAaBestilleFraSkatteetaten().contains(forespoerselInput.inntektsaar + 1)
                if (skalLagesForNesteAarOgsaa) {
                    val forespoerselForNesteAar = forespoerselInput.copy(inntektsaar = forespoerselInput.inntektsaar + 1)
                    handleForespoersel(tx, forespoerselForNesteAar.getMessage(), forespoerselForNesteAar, foedselsnumreWithPersonIdMap, saksbehandler?.ident)
                }
            }
        }.onFailure { exception ->
            logger.error { "Feil ved mottak av forespørsel på skattekort, sjekk feilmeldingen i TEAM LOGS." }
            logger.error(marker = TEAM_LOGS_MARKER, exception) { "Feil ved mottak av forespørsel på skattekort: $message" }
            throw exception
        }
    }

    private fun handleForespoersel(
        tx: TransactionalSession,
        message: String,
        forespoerselInput: ForespoerselInput,
        foedselsnumreWithPersonIdMap: Map<String, PersonId?>,
        brukerId: String?,
    ) {
        val forespoerselId =
            ForespoerselRepository.insert(
                tx = tx,
                forsystem = forespoerselInput.forsystem,
                dataMottatt = message,
            )

        val foedselsnumreWithPersonIdList = foedselsnumreWithPersonIdMap.mapNotNull { (fnr, personId) -> personId?.let { fnr to it } }

        foedselsnumreWithPersonIdList.chunked(CHUNKED_SIZE).forEach { chunk ->
            val personIdList = chunk.map { it.second }
            AbonnementRepository.insertBatch(
                tx = tx,
                forespoerselId = forespoerselId,
                inntektsaar = forespoerselInput.inntektsaar,
                personIdList = personIdList,
            )

            AuditRepository.insertBatch(
                tx,
                tag = AuditTag.MOTTATT_FORESPOERSEL,
                personIdList = personIdList,
                informasjon = "Mottatt forespørsel: $forespoerselId, forsystem: ${forespoerselInput.forsystem.name} på skattekort",
                brukerId = brukerId,
            )
        }
        val (bestillingCount, utsendingCount) =
            handleSkattekortAndUtsending(
                tx,
                inntektsaar = forespoerselInput.inntektsaar,
                forsystem = forespoerselInput.forsystem,
                foedselsnumreWithPersonIdList = foedselsnumreWithPersonIdList,
            )

        logger.info {
            "ForespoerselId: $forespoerselId med total: ${forespoerselInput.fnrList.size} abonnement(er), $bestillingCount bestilling(er), $utsendingCount utsending(er) for inntektsår: ${forespoerselInput.inntektsaar}"
        }
    }

    private fun handleSkattekortAndUtsending(
        tx: TransactionalSession,
        inntektsaar: Int,
        forsystem: Forsystem,
        foedselsnumreWithPersonIdList: List<Pair<String, PersonId>>,
    ): Pair<BestillingCount, UtsendingCount> {
        val skattekortPersonIds =
            SkattekortRepository
                .findAllByPersonId(
                    tx,
                    personIdList = foedselsnumreWithPersonIdList.map { it.second },
                    inntektsaarList = listOf(inntektsaar),
                    showOnlyLatest = true,
                ).map { it.personId }
                .toSet()

        val (personIdWithSkattekort, personIdWithoutSkattekort) = foedselsnumreWithPersonIdList.partition { it.second in skattekortPersonIds }

        val bestillingCount =
            if (personIdWithoutSkattekort.isNotEmpty()) {
                val foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.applicationProperties.gyldigeFnr)
                val (kanBestilles, kanIkkeBestilles) = personIdWithoutSkattekort.partition { (fnr, _) -> foedselsnummerkategori.kanBestilleSkattekort(fnr) }
                if (kanBestilles.isNotEmpty()) {
                    kanBestilles.chunked(CHUNKED_SIZE).forEach { chunk ->
                        BestillingRepository.insertBatch(
                            tx = tx,
                            bestillingList =
                                chunk.map { bestilling ->
                                    Bestilling(
                                        personId = bestilling.second,
                                        fnr = Personidentifikator(bestilling.first),
                                        inntektsaar = inntektsaar,
                                    )
                                },
                        )
                    }
                }
                if (kanIkkeBestilles.isNotEmpty()) {
                    logger.warn { "Fødselsnummer som ikke kan bestille skattekort funnet, sjekk TEAM LOGS" }
                    logger.warn(marker = TEAM_LOGS_MARKER) { "Fødselsnummer som ikke kan bestille skattekort funnet: ${kanIkkeBestilles.joinToString { it.first }}" }
                }
                kanBestilles.size
            } else {
                0
            }

        if (personIdWithSkattekort.isNotEmpty()) {
            personIdWithSkattekort.chunked(CHUNKED_SIZE).forEach { chunk ->
                UtsendingRepository.insertBatch(
                    tx,
                    utsendingList =
                        chunk.map { utsending ->
                            Utsending(null, Personidentifikator(utsending.first), inntektsaar, forsystem)
                        },
                )
            }
        }
        return Pair(bestillingCount, personIdWithSkattekort.size)
    }

    private fun forSentAaBestille(inntektsaar: Int): Boolean {
        // Skatteetatens regel er at man kan bestille skattekort for året før frem til 01.07.
        val currentDate = LocalDate.now()
        val currentYear = currentDate.year
        val cutoffDate = LocalDate.of(currentYear, 7, 1)
        return currentDate.isAfter(cutoffDate) && inntektsaar == currentYear - 1
    }

    private fun parseCopybookMessage(message: String): ForespoerselInput {
        val parts = message.split(DELIMITER).filter { it.isNotBlank() }
        val forsystem =
            when {
                Forsystem.OPPDRAGSSYSTEMET == Forsystem.fromValue(parts[0]) && parts.size > 3 -> Forsystem.OPPDRAGSSYSTEMET_STOR
                else -> Forsystem.fromValue(parts[0])
            }
        val inntektsaar = parts[1].toInt()

        return ForespoerselInput(
            forsystem = forsystem,
            inntektsaar = inntektsaar,
            fnrList = parts.drop(2),
        )
    }
}
