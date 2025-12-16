package no.nav.sokos.skattekort.module.utsending

import java.sql.BatchUpdateException
import javax.sql.DataSource

import io.ktor.server.plugins.di.annotations.Named
import jakarta.jms.Queue
import kotliquery.TransactionalSession
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.TEAM_LOGS_MARKER
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

private val logger = KotlinLogging.logger {}

class UtsendingService(
    private val dataSource: DataSource,
    private val jmsProducerService: JmsProducerService,
    @Named(value = "leveransekoeOppdragZSkattekort") private val leveransekoeOppdragZSkattekort: Queue,
    @Named(value = "leveransekoeOppdragZSkattekortStor") private val leveransekoeOppdragZSkattekortStor: Queue,
    private val featureToggles: UnleashIntegration,
) {
    fun handleUtsending() {
        if (featureToggles.isUtsendingEnabled()) {
            runCatching {
                val utsendinger = getAllUtsendinger()
                utsendingerIKoe.labelValues("uhaandtert").set(utsendinger.size.toDouble())
                utsendingerIKoe.labelValues("feilet").set(utsendinger.filterNot { it.failCount == 0 }.size.toDouble())

                utsendinger.forEach { utsending ->
                    dataSource.transaction { tx ->
                        when (utsending.forsystem) {
                            Forsystem.OPPDRAGSSYSTEMET, Forsystem.OPPDRAGSSYSTEMET_STOR -> {
                                try {
                                    sendSkattekortTilMQ(tx, utsending)
                                    UtsendingRepository.delete(tx, utsending.id!!)
                                    utsendingOppdragzCounter.inc()
                                } catch (e: BatchUpdateException) {
                                    logger.error(marker = TEAM_LOGS_MARKER, e) { "Feil under sending til oppdragz: ${e.message}" }
                                    logger.error("Feil under sending til oppdragz, detaljer er logget til secure log")
                                    dataSource.transaction { errorTx ->
                                        PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                            AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
                                        }
                                        UtsendingRepository.increaseFailCount(errorTx, utsending.id, "SQL-feil, feil er logget til secure log")
                                        feiledeUtsendingerOppdragzCounter.inc()
                                    }
                                } catch (e: Exception) {
                                    logger.error("Feil under sending til oppdragz", e)
                                    dataSource.transaction { errorTx ->
                                        PersonRepository.findPersonByFnr(errorTx, utsending.fnr)?.let { person ->
                                            AuditRepository.insert(errorTx, AuditTag.UTSENDING_FEILET, person.id!!, "Utsending feilet")
                                        }
                                        UtsendingRepository.increaseFailCount(errorTx, utsending.id, e.message ?: "Ukjent feil")
                                        feiledeUtsendingerOppdragzCounter.inc()
                                    }
                                }
                            }

                            Forsystem.MANUELL -> {
                                UtsendingRepository.delete(tx, utsending.id!!)
                            }
                        }
                    }
                }
            }.onFailure { exception ->
                logger.error(exception) { "Feil med utsending til MQ" }
                throw exception
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
        val queue =
            when (utsending.forsystem) {
                Forsystem.OPPDRAGSSYSTEMET -> leveransekoeOppdragZSkattekort
                Forsystem.OPPDRAGSSYSTEMET_STOR -> leveransekoeOppdragZSkattekortStor
                else -> throw IllegalStateException("Utsending til oppdragz er kun støttet for OPPDRAGSSYSTEMET")
            }

        runCatching {
            personId = PersonRepository.findPersonByFnr(tx, utsending.fnr)?.id ?: throw IllegalStateException("Fant ikke personidentifikator")
            val skattekort = SkattekortRepository.findLatestByPersonId(tx, personId, utsending.inntektsaar, adminRole = false)
            val skattekortmelding = Skattekortmelding(skattekort, utsending.fnr.value)
            val copybook = SkattekortFixedRecordFormatter(skattekortmelding, utsending.inntektsaar.toString()).format()

            if (featureToggles.isBevisForSendingEnabled()) {
                UtsendingRepository.lagreBevis(tx, skattekort.id!!, Forsystem.OPPDRAGSSYSTEMET, utsending.fnr, copybook)
            }

            if (!copybook.trim().isEmpty()) {
                jmsProducerService.send(copybook, leveransekoeOppdragZSkattekort, utsendingOppdragzCounter)
                AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "${utsending.forsystem}: Skattekort sendt til ${queue.queueName}")
            } else {
                AuditRepository.insert(tx, AuditTag.UTSENDING_OK, personId, "Oppdragz: Skattekort ikke sendt fordi skattekort-formatet ikke kan uttrykke innholdet")
            }
        }.onFailure { exception ->
            logger.error(exception) { "Feil under sending til oppdragz (${queue.queueName})" }
            throw exception
        }
    }

    fun getAllUtsendinger(): List<Utsending> =
        dataSource.transaction { tx ->
            UtsendingRepository.getAllUtsendinger(tx)
        }

    companion object {
        val utsendingOppdragzCounter =
            counter(
                name = "utsendinger_oppdragz_total",
                helpText = "Utsendinger til oppdrag z",
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
}
