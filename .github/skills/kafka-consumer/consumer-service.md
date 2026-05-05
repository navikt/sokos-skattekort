# Consumer Service Pattern

## Structure

A Kafka consumer service follows this layout:

```
<domain>/kafka/
├── KafkaConsumerService.kt       # Poll loop, lifecycle, metrics
├── <Domain>EndringService.kt     # Message processing / business logic
└── <Domain>HendelseDTO.kt        # Internal DTO mapped from Avro type
```

The consumer service owns the Kafka lifecycle. Business logic lives in a separate service class.

## Consumer Service

```kotlin
@OptIn(ExperimentalAtomicApi::class)
class KafkaConsumerService(
    private val kafkaConfig: KafkaConfig,
    private val processingService: SomeDomainService,
) : AutoCloseable {
    private val kafkaConsumer: KafkaConsumer<String, SomeAvroType> = KafkaConsumer(kafkaConfig.properties)
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
                    val records = kafkaConsumer.poll(Duration.ofSeconds(POLL_DURATION_SECONDS))
                    if (!records.isEmpty) {
                        records.forEach { record ->
                            logger.info { "Record mottatt med offset=${record.offset()}, partisjon=${record.partition()}" }
                            val dto = mapToDTO(record)
                            processingService.process(dto)
                        }
                        kafkaConsumer.commitSync()
                    }
                } catch (e: WakeupException) {
                    if (stopping.load()) break else throw e
                } catch (exception: Exception) {
                    logger.error(exception) { "Error running kafka consumer, unsubscribing and waiting $DELAY_ON_ERROR_SECONDS seconds for retry" }
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

    override fun close() {
        if (stopping.compareAndSet(false, true)) {
            runCatching { kafkaConsumer.wakeup() }
        }
    }
}
```

## Key patterns

### 1. Poll loop with manual commit

- Poll records in a `while` loop guarded by `applicationState.ready` and a `stopping` flag
- Process all records in the batch, then `commitSync()` — never auto-commit
- `MAX_POLL_RECORDS_CONFIG = 1` for at-most-once-style processing when needed

### 2. Graceful shutdown

- `AtomicBoolean` (`stopping`) signals the loop to exit
- `consumer.wakeup()` interrupts a blocking `poll()` and throws `WakeupException`
- Catch `WakeupException` — if `stopping`, break; otherwise re-throw
- `finally` block closes metrics and consumer with `runCatching` to avoid masking exceptions

### 3. Error recovery

On processing failure:
1. Log the error with full exception
2. Unsubscribe from the topic
3. Delay (`kotlinx.coroutines.delay`) before next iteration
4. The loop re-subscribes automatically on the next iteration

This avoids tight retry loops flooding the broker.

### 4. Avro → DTO mapping

Map Avro-generated types to internal DTOs at the consumer boundary:

```kotlin
private fun mapToDTO(record: ConsumerRecord<String, SomeAvroType>): SomeDomainDTO =
    record.value().let { avroObj ->
        SomeDomainDTO(
            id = avroObj.id,
            type = avroObj.type,
            // Map nested Avro types to Kotlin data classes
            nested = avroObj.nested?.let { NestedDTO(it.field1, it.field2) },
        )
    }
```

Internal DTOs are plain Kotlin `data class` types — no Avro dependency leaks into domain code.

### 5. Launching the consumer

The consumer starts as a background coroutine task, gated on `ApplicationState.ready` and a feature flag:

```kotlin
val kafkaProperties = PropertiesConfig.getKafkaProperties()
if (kafkaProperties.enabled) {
    applicationState.onReady = {
        val kafkaConsumerService: KafkaConsumerService by dependencies
        launchBackgroundTask(applicationState) {
            kafkaConsumerService.start(applicationState)
        }
    }
}
```

### 6. Metrics

Bind `KafkaClientMetrics` to the shared Prometheus registry in `init`:

```kotlin
init {
    kafkaClientMetrics.bindTo(Metrics.prometheusMeterRegistry)
}
```

## Constants

Define polling/retry constants as top-level `private const val`:

```kotlin
private const val DELAY_ON_ERROR_SECONDS = 60L
private const val POLL_DURATION_SECONDS = 10L
```
