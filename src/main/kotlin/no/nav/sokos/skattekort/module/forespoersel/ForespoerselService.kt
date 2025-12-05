package no.nav.sokos.skattekort.module.forespoersel

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Year
import javax.sql.DataSource

import kotlin.time.ExperimentalTime
import kotlinx.datetime.Month
import kotlinx.datetime.toKotlinLocalDateTime

import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.Utsending
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
            val forespoerselInput: ForespoerselInput =
                when {
                    message.startsWith("<") -> return@transaction // drop Arena meldinger
                    else -> parseCopybookMessage(message)
                }.let {
                    val kategoriMapper: Foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
                    val ugyldigeFnr = it.fnrList.filterNot(kategoriMapper.erGyldig)
                    if (ugyldigeFnr.isNotEmpty()) {
                        logger.info(marker = TEAM_LOGS_MARKER) { "fjernet ugyldige fnr fra kall: ${ugyldigeFnr.joinToString(", ")}" }
                    }
                    it.copy(fnrList = it.fnrList.minus(ugyldigeFnr))
                }

            logger.info(marker = TEAM_LOGS_MARKER) { "Motta forespørsel på skattekort: $forespoerselInput" }

            handleForespoersel(tx, message, forespoerselInput, saksbehandler?.ident)
            if (skalLagesForNesteAarOgsaa(forespoerselInput)) {
                val forespoerselForNesteAar = forespoerselInput.copy(inntektsaar = forespoerselInput.inntektsaar + 1)
                handleForespoersel(tx, message, forespoerselForNesteAar, saksbehandler?.ident)
            }
        }
    }

    private fun skalLagesForNesteAarOgsaa(forespoerselInput: ForespoerselInput): Boolean {
        val naa = LocalDateTime.now().toKotlinLocalDateTime()
        val iaar = naa.year
        return (
            forespoerselInput.inntektsaar == iaar &&
                naa.month == Month.DECEMBER &&
                naa.day >= 15
        )
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
        forespoerselInput.fnrList.forEach { fnr ->
            val person =
                personService.findOrCreatePersonByFnr(
                    tx = tx,
                    fnr = Personidentifikator(fnr),
                    informasjon = "Mottatt forespørsel: $forespoerselId, forsystem: ${forespoerselInput.forsystem.name} på skattekort",
                    brukerId = brukerId,
                )

            AbonnementRepository.insert(
                tx = tx,
                forespoerselId = forespoerselId,
                inntektsaar = forespoerselInput.inntektsaar,
                personId = person.id!!.value,
            ) ?: throw IllegalStateException("Kunne ikke lage abonnement")

            val skattekort =
                SkattekortRepository
                    .findAllByPersonId(tx, person.id, forespoerselInput.inntektsaar, adminRole = false)
            if (skattekort.isEmpty()) {
                val forSentAaBestille = forSentAaBestille(forespoerselInput.inntektsaar)
                if (forSentAaBestille) logger.warn { "Vi kan ikke lenger bestille skattekort for ${forespoerselInput.inntektsaar}" }
                if (!forSentAaBestille && BestillingRepository.findByPersonIdAndInntektsaar(tx, person.id, forespoerselInput.inntektsaar) == null) {
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
            } else {
                // Skattekort finnes
                val utsending = UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), forespoerselInput.inntektsaar, forespoerselInput.forsystem)
                if (utsending != null) {
                    logger.info {
                        "Utsending eksisterer allerede for personId: ${person.id}, inntektsår: ${forespoerselInput.inntektsaar}, forsystem: ${forespoerselInput.forsystem.name} hopper over opprettelse av utsending"
                    }
                } else {
                    UtsendingRepository.insert(tx, Utsending(null, Personidentifikator(fnr), forespoerselInput.inntektsaar, forespoerselInput.forsystem))
                }
            }
        }
        logger.info { "ForespoerselId: $forespoerselId med total: ${forespoerselInput.fnrList.size} abonnement(er), $bestillingCount bestilling(er)" }
    }

    private fun forSentAaBestille(inntektsaar: Int): Boolean {
        // Skatteetatens regel er at man kan bestille skattekort for året før frem til 01.07.
        val currentYear =
            Year
                .now()
                .value
        val currentMonth =
            LocalDate
                .now()
                .monthValue
        val forSentAaBestilleForFjoraaret = currentMonth >= 7 && inntektsaar == currentYear - 1
        val endaTidligere = inntektsaar < currentYear - 1
        return forSentAaBestilleForFjoraaret || endaTidligere
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
