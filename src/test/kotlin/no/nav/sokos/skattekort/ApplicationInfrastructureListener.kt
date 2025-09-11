package no.nav.sokos.skattekort

import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.listeners.AfterSpecListener
import io.kotest.core.listeners.AfterTestListener
import io.kotest.core.listeners.BeforeSpecListener
import io.kotest.core.spec.Spec
import io.kotest.core.test.TestCase
import io.kotest.engine.test.TestResult
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ
import org.testcontainers.containers.PostgreSQLContainer

import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.config.JmsListener

/*
Abstraherer management av infrastrukturen en applikasjon trenger. Formålet med denne er å
unngå at tester bruker globale variable fra klasser som ikke er nevnt i testen direkte,
som DbListener.dataSource.
 */
object ApplicationInfrastructureListener : BeforeSpecListener, AfterTestListener, AfterSpecListener {
    override suspend fun beforeSpec(spec: Spec) {
        println("foo")
        JmsListener.beforeSpec(spec)
        println("xyzzy")
        DbListener.beforeSpec(spec)
    }

    override suspend fun afterAny(
        testCase: TestCase,
        result: TestResult,
    ) {
        JmsListener.afterAny(testCase, result)
    }

    override suspend fun afterSpec(spec: Spec) {
        DbListener.afterSpec(spec)
    }

    fun jmsServer(): EmbeddedActiveMQ = JmsListener.server

    fun jmsConnectionFactory(): ConnectionFactory = JmsListener.connectionFactory

    fun bestillingsQueue(): Queue = JmsListener.bestillingsQueue

    fun allQueues(): List<Queue> = JmsListener.allQueues

    fun dbContainer(): PostgreSQLContainer<Nothing> = DbListener.container

    fun dbDataSource(): HikariDataSource = DbListener.dataSource
}
