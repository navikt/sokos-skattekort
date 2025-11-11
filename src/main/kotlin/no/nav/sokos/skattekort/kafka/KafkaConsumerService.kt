package no.nav.sokos.skattekort.kafka

import java.time.Duration

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

import io.micrometer.core.instrument.binder.kafka.KafkaClientMetrics
import mu.KotlinLogging
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer

import no.nav.person.pdl.leesah.Personhendelse
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.metrics.Metrics

private val logger = KotlinLogging.logger {}
private const val DELAY_ON_ERROR_SECONDS = 60L
private const val DELAY_KAFKA_START = 500L
private const val POLL_DURATION_SECONDS = 10L

class KafkaConsumerService(
    private val kafkaConfig: KafkaConfig,
    private val identifikatorEndringService: IdentifikatorEndringService,
) {
    private val kafkaConsumer: KafkaConsumer<String, Personhendelse> = KafkaConsumer(kafkaConfig.properties)
    private val kafkaClientMetrics: KafkaClientMetrics = KafkaClientMetrics(kafkaConsumer)

    init {
        kafkaClientMetrics.bindTo(Metrics.prometheusMeterRegistry)
    }

    suspend fun start(applicationState: ApplicationState) {
        kafkaConsumer.use { consumer ->
            consumer.subscribe(listOf(kafkaConfig.topic))

            logger.info { "Starter kafka consumer for topic=${kafkaConfig.topic}" }
            while (applicationState.ready) {
                if (kafkaConsumer.subscription().isEmpty()) {
                    kafkaConsumer.subscribe(listOf(kafkaConfig.topic))
                }

                runCatching {
                    val consumerRecords: ConsumerRecords<String, Personhendelse> = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                    if (!consumerRecords.isEmpty) {
                        consumerRecords.forEach { record ->
                            logger.info { "Record mottatt med offset = ${record.offset()}, partisjon = ${record.partition()}, topic = ${record.topic()}" }
                            val personHendelseDTO = mapToPersonHendelseDTO(record)
                            identifikatorEndringService.processIdentifikatorEndring(personHendelseDTO)
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
}
