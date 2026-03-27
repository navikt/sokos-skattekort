package no.nav.sokos.skattekort.person.kafka

import java.time.Duration

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.errors.WakeupException

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.infrastructure.Metrics

private val logger = KotlinLogging.logger {}
private const val DELAY_ON_ERROR_SECONDS = 60L
private const val DELAY_KAFKA_START = 500L
private const val POLL_DURATION_SECONDS = 10L

@OptIn(ExperimentalAtomicApi::class)
class KafkaConsumerService(
    private val kafkaConfig: KafkaConfig,
    private val identifikatorEndringService: IdentifikatorEndringService,
) : AutoCloseable {
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse> = KafkaConsumer(kafkaConfig.properties)
    private val kafkaClientMetrics: KafkaClientMetrics = KafkaClientMetrics(kafkaConsumer)
    private val stopping = AtomicBoolean(false)

    init {
        kafkaClientMetrics.bindTo(Metrics.prometheusMeterRegistry)
    }

    suspend fun start(applicationState: ApplicationState) {
        try {
            kafkaConsumer.subscribe(listOf(kafkaConfig.topic))

            logger.info { "Starter kafka consumer for topic=${kafkaConfig.topic}" }
            while (applicationState.ready && !stopping.load()) {
                if (kafkaConsumer.subscription().isEmpty()) {
                    kafkaConsumer.subscribe(listOf(kafkaConfig.topic))
                }
                try {
                    val consumerRecords: ConsumerRecords<String, Personhendelse> = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                    if (!consumerRecords.isEmpty) {
                        consumerRecords.forEach { record ->
                            logger.info { "Record mottatt med offset = ${record.offset()}, partisjon = ${record.partition()}, topic = ${record.topic()}" }
                            val personHendelseDTO = mapToPersonHendelseDTO(record)
                            identifikatorEndringService.processIdentifikatorEndring(personHendelseDTO)
                        }
                        kafkaConsumer.commitSync()
                    }
                } catch (e: WakeupException) {
                    if (stopping.load()) break else throw e
                } catch (exception: Exception) {
                    logger.error(exception) { "Error running kafka consumer for ${kafkaConfig.topic}, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry" }
                    kafkaConsumer.unsubscribe()
                    if (applicationState.ready) {
                        delay(DELAY_ON_ERROR_SECONDS.seconds)
                    }
                }
            }
        } finally {
            runCatching { kafkaClientMetrics.close() }.onFailure { logger.warn(it) { "Failed to close Kafka client metrics" } }
            runCatching { kafkaConsumer.close() }.onFailure { logger.warn(it) { "Failed to close Kafka consumer" } }
        }
    }

    private fun mapToPersonHendelseDTO(record: ConsumerRecord<String, Personhendelse>): PersonHendelseDTO =
        record.value().let { hendelse ->
            PersonHendelseDTO(
                hendelseId = hendelse.hendelseId,
                personidenter = hendelse.personidenter.toList(),
                opplysningstype = hendelse.opplysningstype,
                endringstype = EndringstypeDTO.valueOf(hendelse.endringstype.name),
                folkeregisteridentifikator =
                    hendelse.folkeregisteridentifikator?.let { identifikator ->
                        FolkeregisteridentifikatorDTO(
                            identifikasjonsnummer = identifikator.identifikasjonsnummer,
                            type = identifikator.type,
                            status = identifikator.status,
                        )
                    },
            )
        }

    override fun close() {
        if (stopping.compareAndSet(false, true)) {
            runCatching { kafkaConsumer.wakeup() }
        }
    }
}
