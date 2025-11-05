package no.nav.sokos.skattekort.kafka

import java.time.Duration

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer

import no.nav.person.pdl.aktor.v2.Aktor
import no.nav.person.pdl.aktor.v2.Type
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.metrics.Metrics
import no.nav.sokos.skattekort.module.person.AktorService

private val logger = KotlinLogging.logger {}
private const val DELAY_ON_ERROR_SECONDS = 60L
private const val POLL_DURATION_SECONDS = 10L

class KafkaConsumerService(
    private val kafkaConfig: KafkaConfig,
    private val aktorService: AktorService,
) {
    private val kafkaConsumer: KafkaConsumer<String, Aktor> = KafkaConsumer(kafkaConfig.properties)
    private val kafkaClientMetrics: KafkaClientMetrics = KafkaClientMetrics(kafkaConsumer)

    init {
        kafkaClientMetrics.bindTo(Metrics.prometheusMeterRegistry)
    }

    suspend fun start(applicationState: ApplicationState) {
        kafkaConsumer.use { consumer ->
            consumer.subscribe(listOf(kafkaConfig.topic))

            while (applicationState.ready) {
                if (kafkaConsumer.subscription().isEmpty()) {
                    kafkaConsumer.subscribe(listOf(kafkaConfig.topic))
                }

                runCatching {
                    val consumerRecords: ConsumerRecords<String, Aktor> = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                    if (!consumerRecords.isEmpty) {
                        logger.info("Mottatt ${consumerRecords.count()} meldinger fra PDL")
                        consumerRecords.forEach { record ->
                            logger.info { "Record mottatt med offset = ${record.offset()}, partisjon = ${record.partition()}, topic = ${record.topic()}" }
                            val identifikatorList = getIdentifikatorList(record)
                            aktorService.processIdentChanging(identifikatorList)
                        }
                        kafkaConsumer.commitSync()
                    }
                }.onFailure { exception ->
                    logger.error(exception) { "Error running kafka consumer for pdl-aktor, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry" }
                    kafkaConsumer.unsubscribe()
                    delay(DELAY_ON_ERROR_SECONDS.seconds)
                }
            }
        }
    }

    private fun getIdentifikatorList(record: ConsumerRecord<String, Aktor>): List<IdentifikatorDTO> =
        record.value()?.let { aktor ->
            aktor.identifikatorer
                .map { identifikator ->
                    IdentifikatorDTO(
                        idnummer = identifikator.idnummer,
                        gjeldende = identifikator.gjeldende,
                        type =
                            when (identifikator.type) {
                                Type.FOLKEREGISTERIDENT -> IdentType.FOLKEREGISTERIDENT
                                Type.NPID -> IdentType.NPID
                                Type.AKTORID -> IdentType.AKTORID
                            },
                    )
                }
        } ?: emptyList()
}
