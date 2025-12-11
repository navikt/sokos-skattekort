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
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchRepository
import no.nav.sokos.skattekort.module.skattekort.BestillingBatchStatus
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.skattekort.Status
import no.nav.sokos.skattekort.module.utsending.Utsending
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.SQLUtils.transaction

private const val FORESPOERSEL_DELIMITER = ";"
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
        dataSource.transaction { tx ->
            val forespoerselInput: List<ForespoerselInput> =
                when {
                    message.startsWith("<") -> return@transaction

                    // drop Arena meldinger
                    else -> parseCopybookMessage(message)
                }.map {
                    val kategoriMapper: Foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
                    val ugyldigeFnr = it.fnrList.filterNot(kategoriMapper.erGyldig)
                    if (ugyldigeFnr.isNotEmpty()) {
                        logger.info(marker = TEAM_LOGS_MARKER) { "fjernet ugyldige fnr fra kall: ${ugyldigeFnr.joinToString(", ")}" }
                    }
                    it.copy(fnrList = it.fnrList.minus(ugyldigeFnr))
                }

            logger.info(marker = TEAM_LOGS_MARKER) { "Motta forespørsel på skattekort: $forespoerselInput" }

            forespoerselInput.forEach { forespoersel ->
                handleForespoersel(tx, message, forespoersel, saksbehandler?.ident)
                if (skalLagesForNesteAarOgsaa(forespoersel)) {
                    val forespoerselForNesteAar = forespoersel.copy(inntektsaar = forespoersel.inntektsaar + 1)
                    handleForespoersel(tx, message, forespoerselForNesteAar, saksbehandler?.ident)
                }
            }
        }
    }

    fun statusForespoeresel(
        fnr: String,
        aar: Int,
        forsystem: String,
        saksbehandler: Saksbehandler? = null,
    ): Status {
        val kategoriMapper: Foedselsnummerkategori = Foedselsnummerkategori.valueOf(PropertiesConfig.getApplicationProperties().gyldigeFnr)
        if (!kategoriMapper.erGyldig(fnr)) {
            return Status.UGYLDIG_FNR
        }
        val person: Person? =
            dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, Personidentifikator(fnr))
            }
        if (person == null) return Status.IKKE_FNR

        val bestilling: Bestilling? =
            dataSource.transaction { tx ->
                BestillingRepository.findByPersonIdAndInntektsaar(tx, person.id!!, aar)
            }
        if (bestilling != null) {
            if (bestilling.bestillingsbatchId == null) {
                return Status.IKKE_BESTILT
            }

            val batch =
                dataSource.transaction { tx ->
                    BestillingBatchRepository.findById(tx, bestilling.bestillingsbatchId.id)
                }

            if (batch?.status == BestillingBatchStatus.Ny.value) {
                return Status.BESTILT
            } else if (batch?.status == BestillingBatchStatus.Feilet.value) {
                return Status.FEILET_I_BESTILLING
            }
        }
        val skattekort =
            dataSource.transaction { tx ->
                SkattekortRepository.findAllByPersonId(tx, person.id!!, aar, adminRole = false)
            }

        if (skattekort.isNotEmpty()) {
            val utsending =
                dataSource.transaction { tx ->
                    UtsendingRepository.findByPersonIdAndInntektsaar(tx, Personidentifikator(fnr), aar, Forsystem.fromValue(forsystem))
                }
            return if (utsending != null) {
                Status.VENTER_PAA_UTSENDING
            } else {
                Status.SENDT_FORSYSTEM
            }
        }
        return Status.UKJENT
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
    private fun parseCopybookMessage(message: String): List<ForespoerselInput> = message.lines().map { row -> ForespoerselInput.fromString(row) }.filterNotNull()

    fun cronForespoerselInput() {
        if (featureToggles.isForespoerselInputEnabled()) {
            val forespoerselInput: List<ForespoerselInput> =
                dataSource.transaction { tx ->
                    val returverdi = ForespoerselRepository.getAllForespoerselInput(tx)
                    ForespoerselRepository.deleteAllForespoerselInput(tx)
                    returverdi
                }
            forespoerselInput.forEach { forespoerselInput ->
                try {
                    dataSource.transaction { tx ->
                        val message = "${forespoerselInput.forsystem};${forespoerselInput.inntektsaar};${forespoerselInput.fnrList.joinToString(",")}"
                        handleForespoersel(tx, message, forespoerselInput, null)
                    }
                } catch (e: Exception) {
                    logger.error("Exception under håndtering av forespoersel fra database: ${e.message}", e)
                }
            }
        }
    }

    data class ForespoerselInput(
        val forsystem: Forsystem,
        val inntektsaar: Int,
        val fnrList: List<String>,
    ) {
        companion object {
            fun fromString(message: String): ForespoerselInput? {
                if (message.trim().isEmpty()) {
                    return null
                }
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
        }
    }
}
