package no.nav.sokos.skattekort.module.utsending

import javax.sql.DataSource

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.Queue
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.infrastructure.Metrics.counter
import no.nav.sokos.skattekort.infrastructure.Metrics.gauge
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.module.forespoersel.Forsystem
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.skattekort.SkattekortRepository
import no.nav.sokos.skattekort.module.utsending.oppdragz.SkattekortFixedRecordFormatter
import no.nav.sokos.skattekort.module.utsending.oppdragz.Skattekortmelding
import no.nav.sokos.skattekort.mq.JmsProducerService
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class UtsendingService(
    private val dataSource: DataSource,
    private val jmsProducerService: JmsProducerService,
    @Named("leveransekoeOppdragZSkattekort") val leveransekoeOppdragZSkattekort: Queue,
    @Named("leveransekoeDarePocSkattekort") val leveransekoeDarePocSkattekort: Queue,
    private val featureToggles: UnleashIntegration,
) {
    private val logger = KotlinLogging.logger {}

    companion object {
        val utsendingOppdragzCounter =
            counter(
                name = "utsendinger_oppdragz_total",
                helpText = "Utsendinger til oppdrag z",
            )

        val utsendingDarePocCounter =
            counter(
                name = "utsendinger_dare_poc_total",
                helpText = "Utsendinger til DARE POC",
            )

        val feiledeUtsendingerOppdragzCounter =
            counter(
                name = "utsendinger_oppdragz_feil_total",
                helpText = "Feilede forsøk på utsendinger til oppdrag z",
            )

        val utsendingerIKoe =
            gauge(
                name = "utsendinger_i_koe",
                helpText = "Utsendinger i kø, enda ikke håndtert",
                labelNames = "status",
            )
    }

    fun handleUtsending() {
        if (featureToggles.isUtsendingEnabled()) {
            runCatching {
                val utsendinger: List<Utsending> = dataSource.transaction { tx -> UtsendingRepository.getAllUtsendinger(tx) }
                utsendingerIKoe.labelValues("uhaandtert").set(utsendinger.size.toDouble())
                utsendingerIKoe.labelValues("feilet").set(utsendinger.filterNot { it.failCount == 0 }.size.toDouble())

                utsendinger
                    .forEach { utsending ->
                        dataSource.transaction { tx ->
                            when (utsending.forsystem) {
                                Forsystem.OPPDRAGSSYSTEMET, Forsystem.DARE_POC -> sendSkattekortTilMQ(tx, utsending)
                                Forsystem.MANUELL -> UtsendingRepository.delete(tx, utsending.id!!)
                            }
                        }
                    }
            }.onFailure { exception ->
                logger.error(exception) { "Feil med utsending til MQ" }
            }
        } else {
            logger.debug("Utsending er disablet")
        }
        dataSource.transaction { tx ->
            UtsendingRepository.slettGamleBevis(tx)
        }
    }

    private fun sendSkattekortTilMQ(
        tx: TransactionalSession,
        utsending: Utsending,
    ) {
        var personId: PersonId? = null
        runCatching {
            personId = PersonRepository.findPersonByFnr(tx, utsending.fnr)?.id ?: throw IllegalStateException("Fant ikke personidentifikator")
            val skattekort = SkattekortRepository.findLatestByPersonId(tx, personId, utsending.inntektsaar, adminRole = false)
            val skattekortmelding = Skattekortmelding(skattekort, utsending.fnr.value)
            val copybook = SkattekortFixedRecordFormatter(skattekortmelding, utsending.inntektsaar.toString()).format()

            if (featureToggles.isBevisForSendingEnabled()) {
                UtsendingRepository.lagreBevis(tx, skattekort.id!!, Forsystem.OPPDRAGSSYSTEMET, utsending.fnr, copybook)
            }

            when (utsending.forsystem) {
                Forsystem.OPPDRAGSSYSTEMET -> jmsProducerService.send(copybook, leveransekoeOppdragZSkattekort, utsendingOppdragzCounter)
                Forsystem.DARE_POC -> jmsProducerService.send(copybook, leveransekoeDarePocSkattekort, utsendingDarePocCounter)
                else -> throw IllegalStateException("Utsending til oppdragz er kun støttet for OPPDRAGSSYSTEMET/DARE_POC")
            }
            AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "${utsending.forsystem}: Skattekort sendt")
            UtsendingRepository.delete(tx, utsending.id!!)
        }.onFailure { exception ->
            logger.error(exception) { "Feil under sending til oppdragz" }
            personId?.let { id ->
                dataSource.transaction { errorsession ->
                    AuditRepository.insert(errorsession, AuditTag.UTSENDING_FEILET, id, "Oppdragz: Utsending feilet: $exception")
                    UtsendingRepository.increaseFailCount(errorsession, utsending.id, exception.message ?: "Ukjent feil")
                    feiledeUtsendingerOppdragzCounter.inc()
                }
            }
        }
    }
}
