package no.nav.sokos.skattekort.infrastructure

import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

object KafkaListener : BeforeSpecListener {
    private val kafka =
        KafkaContainer(DockerImageName.parse("apache/kafka:latest"))
            .withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
            .withReuse(reuseConfig.reuse)
            .withLabel("reuse.UUID", reuseConfig.reuseLabel)
            .start()

    override suspend fun beforeSpec(spec: Spec) {
        // System.setProperty("kafka.bootstrap.servers", kafka)
    }
}
