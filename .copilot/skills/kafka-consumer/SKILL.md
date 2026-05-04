---
name: kafka-consumer
description: "Kafka consumer patterns for NAIS services: Avro deserialization, poll loop with manual commit, graceful shutdown, Schema Registry with SSL, consumer lifecycle, and message processing. Accepts prompts in Norwegian and English. (Kafka, konsument, Avro, Schema Registry, meldinger, polling)"
---

# Kafka Consumer

Kafka consumer patterns for this codebase. Uses the plain `kafka-clients` library with Confluent Avro deserialization — no Spring Kafka, no streams DSL. Detailed examples live in the sub-files — load them on demand.

## Quick reference

| Concept | Implementation |
|---|---|
| Client library | `org.apache.kafka:kafka-clients` (plain Java client) |
| Serialization | Confluent `KafkaAvroDeserializer` + Avro-generated classes |
| Schema Registry | Confluent Schema Registry with basic auth over SSL |
| Consumer pattern | Manual poll loop (`while (ready)`) with `commitSync()` |
| Threading | `suspend fun start()` launched as background coroutine task |
| Shutdown | `AtomicBoolean` stop flag + `consumer.wakeup()` for clean exit |
| Error handling | Catch → log → unsubscribe → delay → re-subscribe on next iteration |
| Metrics | `KafkaClientMetrics` bound to Micrometer `PrometheusMeterRegistry` |
| Config source | `PropertiesConfig.getKafkaProperties()` via HOCON |
| Feature toggle | Consumer only starts when `kafkaProperties.enabled == true` |

## Sub-files

- [consumer-service.md](consumer-service.md) — poll loop, lifecycle, graceful shutdown, message processing
- [config-and-avro.md](config-and-avro.md) — KafkaConfig, Avro schema setup, SSL/Schema Registry configuration

## Boundaries

### Always
- Manual `commitSync()` after processing — never auto-commit
- `ENABLE_AUTO_COMMIT_CONFIG = false`
- `ISOLATION_LEVEL_CONFIG = read_committed`
- Graceful shutdown via `AtomicBoolean` + `wakeup()`
- Map Avro-generated types to internal DTOs at the consumer boundary
- Bind `KafkaClientMetrics` to the shared `Metrics.prometheusMeterRegistry`
- Wrap the consumer in `AutoCloseable` for clean resource cleanup
- Launch consumer as a background task via `launchBackgroundTask` when `ApplicationState.ready`

### Never
- Spring Kafka or Kafka Streams — use plain `kafka-clients`
- Auto-commit (`enable.auto.commit = true`)
- `read_uncommitted` isolation
- Process messages without committing offsets
- Block the main application thread with the consumer loop
- Swallow exceptions silently — always log before retry/delay
