package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import io.ktor.server.application.Application
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.di.dependencies
import jakarta.jms.Queue
import mu.KotlinLogging

import no.nav.sokos.skattekort.audit.AuditLogger
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.JobTaskConfig
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.config.MQConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.infrastructure.MetricsService
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.kafka.IdentifikatorEndringService
import no.nav.sokos.skattekort.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.BestillingService
import no.nav.sokos.skattekort.module.skattekort.SkattekortPersonService
import no.nav.sokos.skattekort.module.status.StatusService
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.pdl.PdlClientService
import no.nav.sokos.skattekort.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.util.launchBackgroundTask

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)

    commonConfig()

    PropertiesConfig.initEnvConfig(applicationConfig)
    val applicationProperties = PropertiesConfig.getApplicationProperties()
    logger.info { "Application started with environment: ${applicationProperties.environment}" }

    DatabaseConfig.migrate()

    dependencies {
        provide { createHttpClient() } cleanup { client ->
            client.close()
        }
        provide { DatabaseConfig.dataSource }
        provide { KafkaConfig() }
        provide { PropertiesConfig.getUnleashProperties() }
        provide { PropertiesConfig.getApplicationProperties() }
        provide(MaskinportenTokenClient::class)
        provide(AuditLogger::class)

        provide { MQConfig.connectionFactory }
        provide<String>(name = "pdlUrl") { PropertiesConfig.getPdlProperties().pdlUrl }
        provide<Queue>(name = "forespoerselQueue") {
            MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
        }
        provide<Queue>(name = "forespoerselBoqQueue") {
            MQQueue("${PropertiesConfig.getMQProperties().fraForSystemQueue}_BOQ")
        }
        provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
            val queue = MQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
            queue.messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
            queue
        }
        provide<Queue>(name = "leveransekoeOppdragZSkattekortStor") {
            val queue = MQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekortStor)
            queue.messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
            queue
        }
        provide<AzuredTokenClient>(name = "pdlAzuredTokenClient") {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.getPdlProperties().pdlScope)
        }
        provide(UnleashIntegration::class)
        provide(StatusService::class)
        provide(PersonService::class)
        provide(ForespoerselService::class)
        provide(ForespoerselListener::class)
        provide(UtsendingService::class)
        provide(BestillingService::class)
        provide(SkatteetatenClient::class)
        provide(SkattekortPersonService::class)
        provide(KafkaConsumerService::class)
        provide(PdlClientService::class)
        provide(IdentifikatorEndringService::class)
        provide(MetricsService::class)
    }

    securityConfig()
    routingConfig(applicationState)

    val forespoerselListener: ForespoerselListener by dependencies
    forespoerselListener.start()

    if (PropertiesConfig.SchedulerProperties().enabled) {
        val bestillingService: BestillingService by dependencies
        val utsendingService: UtsendingService by dependencies
        val scheduledTaskService = ScheduledTaskService(DatabaseConfig.dataSourceReadCommit)
        val metricsService: MetricsService by dependencies
        val forespoerselService: ForespoerselService by dependencies

        JobTaskConfig
            .scheduler(
                bestillingService,
                utsendingService,
                scheduledTaskService,
                metricsService,
                forespoerselService,
                DatabaseConfig.dataSourceReadCommit,
            ).start()
    }

    val kafkaProperties = PropertiesConfig.getKafkaProperties()
    if (kafkaProperties.enabled) {
        applicationState.onReady = {
            val kafkaConsumerService: KafkaConsumerService by dependencies
            launchBackgroundTask(applicationState) {
                kafkaConsumerService.start(applicationState)
            }
        }
    }

    logger.info { "Kafka consumer is enabled: ${kafkaProperties.enabled}" }
}
