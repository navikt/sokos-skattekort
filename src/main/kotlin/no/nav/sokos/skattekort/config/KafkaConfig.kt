package no.nav.sokos.skattekort.config

import java.util.Properties

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol

object KafkaConfig {
    val kafkaConsumerConfig: KafkaConsumerConfig by lazy {
        val kafkaProperties = PropertiesConfig.getKafkaProperties()
        KafkaConsumerConfig(
            topic = kafkaProperties.topic,
            properties = initProperties(kafkaProperties),
        )
    }

    data class KafkaConsumerConfig(
        val topic: String,
        val properties: Properties = Properties(),
    )

    private fun initProperties(kafkaProperties: PropertiesConfig.KafkaProperties): Properties =
        Properties().apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumerGroupId)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer::class.java.name)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1")
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "200000")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.offsetReset)
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")
            put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true)

            if (kafkaProperties.useSSLSecurity) {
                put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
                put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
                put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
                put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
                put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaProperties.truststorePath)
                put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaProperties.credstorePassword)
                put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaProperties.keystorePath)
                put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaProperties.credstorePassword)
            }
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.brokers)
            put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.schemaRegistry)
            put(SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            put(SchemaRegistryClientConfig.USER_INFO_CONFIG, "${kafkaProperties.schemaRegistryUser}:${kafkaProperties.schemaRegistryPassword}")
        }
}
