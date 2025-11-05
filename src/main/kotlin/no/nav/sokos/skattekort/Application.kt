package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.dependencies
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.JobTaskConfig
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.config.MQConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.SftpConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.httpClient
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.person.AktorService
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.BestillingService
import no.nav.sokos.skattekort.module.skattekortpersonapi.v1.SkattekortPersonService
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.sftp.SftpService
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.util.launchBackgroundTask

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)

    PropertiesConfig.initEnvConfig(applicationConfig)
    val applicationProperties = PropertiesConfig.getApplicationProperties()
    val useAuthentication = applicationProperties.useAuthentication
    logger.info { "Application started with environment: ${applicationProperties.environment}, useAuthentication: $useAuthentication" }

    DatabaseConfig.migrate()

    dependencies {
        provide { httpClient }
        provide { DatabaseConfig.dataSource }
        provide { SftpConfig() }
        provide { KafkaConfig() }
        provide(SftpService::class)
        provide(MaskinportenTokenClient::class)

        provide { MQConfig.connectionFactory }
        provide<Queue>(name = "forespoerselQueue") {
            MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
        }
        provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
            MQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
        }

        provide(PersonService::class)
        provide(ForespoerselService::class)
        provide(ForespoerselListener::class)
        provide(UtsendingService::class)
        provide(BestillingService::class)
        provide(SkatteetatenClient::class)
        provide(ScheduledTaskService::class)
        provide(SkattekortPersonService::class)
        provide(AktorService::class)
        provide(KafkaConsumerService::class)
    }

    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)

    val sftpService: SftpService by dependencies
    logger.info { "SFTP connection is enabled: ${sftpService.isSftpConnectionEnabled()}" }

    val forespoerselListener: ForespoerselListener by dependencies
    forespoerselListener.start()

    if (PropertiesConfig.SchedulerProperties().enabled) {
        val bestillingService: BestillingService by dependencies
        val utsendingService: UtsendingService by dependencies
        val scheduledTaskService: ScheduledTaskService by dependencies
        val dataSource: HikariDataSource by dependencies
        JobTaskConfig
            .scheduler(
                bestillingService,
                utsendingService,
                scheduledTaskService,
                dataSource,
            ).start()
    }

    val kafkaProperties = PropertiesConfig.getKafkaProperties()
    if (kafkaProperties.enabled) {
        monitor.subscribe(ApplicationStarted) {
            applicationState.ready = true
            val kafkaConsumerService: KafkaConsumerService by dependencies
            launchBackgroundTask(applicationState) {
                kafkaConsumerService.start(applicationState)
            }
        }
    }
    logger.info { "Kafka consumer is enabled: ${kafkaProperties.enabled}" }
}
