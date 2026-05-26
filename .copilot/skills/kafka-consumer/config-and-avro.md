# Kafka Config & Avro

## KafkaConfig class

Configuration lives in a dedicated class that reads from `PropertiesConfig` and builds a `java.util.Properties` for the consumer:

```kotlin
class KafkaConfig(
    private val kafkaProperties: PropertiesConfig.KafkaProperties = PropertiesConfig.getKafkaProperties(),
) {
    val topic: String by lazy { kafkaProperties.topic }

    val properties: Properties by lazy { initProperties(kafkaProperties) }

    private fun initProperties(kafkaProperties: PropertiesConfig.KafkaProperties): Properties =
        Properties().apply {
            // Consumer group
            put(ConsumerConfig.GROUP_ID_CONFIG, kafkaProperties.consumerGroupId)

            // Avro deserialization
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer::class.java.name)
            put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true)

            // Consumer behavior
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "1")
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "200000")
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, kafkaProperties.offsetReset)
            put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed")

            // Broker
            put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.brokers)

            // Schema Registry
            put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, kafkaProperties.schemaRegistry)
            put(SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE, "USER_INFO")
            put(SchemaRegistryClientConfig.USER_INFO_CONFIG, "${kafkaProperties.schemaRegistryUser}:${kafkaProperties.schemaRegistryPassword}")

            // SSL
            put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
            put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
            put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
            put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
            put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, kafkaProperties.truststorePath)
            put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, kafkaProperties.credstorePassword)
            put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, kafkaProperties.keystorePath)
            put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, kafkaProperties.credstorePassword)
        }
}
```

### Required consumer settings

| Property | Value | Why |
|---|---|---|
| `ENABLE_AUTO_COMMIT_CONFIG` | `false` | Manual `commitSync()` after processing |
| `ISOLATION_LEVEL_CONFIG` | `read_committed` | Only read committed transactional messages |
| `SPECIFIC_AVRO_READER_CONFIG` | `true` | Deserialize to generated Avro classes (not `GenericRecord`) |
| `SECURITY_PROTOCOL_CONFIG` | `SSL` | NAIS Kafka requires mTLS |

## Avro schemas

Avro schemas live in `src/main/avro/` and are compiled to Java classes by the `io.github.androa.gradle.plugin.avro` Gradle plugin.

```
src/main/avro/
└── <namespace>/
    └── <SchemaName>.avsc
```

The generated classes end up on the classpath and are used as value types in `KafkaConsumer<String, GeneratedAvroType>`.

### Gradle plugin

```kotlin
plugins {
    id("io.github.androa.gradle.plugin.avro") version "<version>"
}

generateAvro {
    schemas.from(layout.projectDirectory.dir("src/main/avro/"))
}
```

## NAIS Kafka on SSL

NAIS provisions Kafka credentials as files on disk. The paths and passwords come from environment variables, read via HOCON config:

- `KAFKA_TRUSTSTORE_PATH` → JKS truststore
- `KAFKA_KEYSTORE_PATH` → PKCS12 keystore
- `KAFKA_CREDSTORE_PASSWORD` → shared password for both stores
- `KAFKA_SCHEMA_REGISTRY` → Schema Registry URL
- `KAFKA_SCHEMA_REGISTRY_USER` / `KAFKA_SCHEMA_REGISTRY_PASSWORD` → basic auth

## Dependencies

```kotlin
// build.gradle.kts
val kafkaClientsVersion = "8.1.1-ce"
val avroVersion = "1.12.1"
val kafkaAvroSerializerVersion = "8.1.1"

implementation("org.apache.kafka:kafka-clients:$kafkaClientsVersion")
implementation("org.apache.avro:avro:$avroVersion")
implementation("io.confluent:kafka-avro-serializer:$kafkaAvroSerializerVersion")
```

Confluent packages require the Confluent Maven repository:

```kotlin
repositories {
    maven { url = uri("https://packages.confluent.io/maven/") }
}
```
