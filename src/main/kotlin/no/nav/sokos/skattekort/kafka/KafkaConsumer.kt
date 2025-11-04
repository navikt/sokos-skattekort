package no.nav.sokos.skattekort.kafka

import mu.KotlinLogging

import no.nav.sokos.skattekort.config.KafkaConfig

private val logger = KotlinLogging.logger {}

class KafkaConsumer(
    private val kafkaConsumerConfig: KafkaConfig.KafkaConsumerConfig,
)
