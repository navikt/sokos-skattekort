package no.nav.sokos.skattekort

import javax.sql.DataSource

import kotlinx.coroutines.runBlocking

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import io.ktor.server.application.Application
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
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.infrastructure.MetricsService
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.infrastructure.scheduler.ScheduledTaskService
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.kafka.IdentifikatorEndringService
import no.nav.sokos.skattekort.person.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingBatchService
import no.nav.sokos.skattekort.skattekortbestilling.StatusService
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.skattekortkonvertering.KonverteringService
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.util.launchBackgroundTask
import no.nav.sokos.skattekort.utsending.UtsendingService

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
        provide<String>(name = "pdlUrl") { PropertiesConfig.getPdlProperties().pdlUrl }
        provide<AzuredTokenClient>(name = "pdlAzuredTokenClient") {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.getPdlProperties().pdlScope)
        }
        provide<String>(name = "tilgangsmaskinUrl") { PropertiesConfig.getTilgangsmaskinProperties().tilgangsmaskinUrl }
        provide<AzuredTokenClient>(name = "tilgangsmaksinAzuredTokenClient") {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.getTilgangsmaskinProperties().tilgangsmaskinScope)
        }
        provide(StatusService::class)
        provide(PersonService::class)
        provide(ForespoerselService::class)
        provide(ForespoerselListener::class)
        provide(UtsendingService::class)
        provide(BestillingBatchService::class)
        provide(BestillingService::class)
        provide(KonverteringService::class)
        provide(SkatteetatenClient::class)
        provide(SkattekortService::class)
        provide(KafkaConsumerService::class)
        provide(PdlClientService::class)
        provide(TilgangsmaskinClientService::class)
        provide(IdentifikatorEndringService::class)
        provide(MetricsService::class)
        provide<UnleashIntegration> {
            UnleashIntegration { enabled ->
                val forespoerselListener: ForespoerselListener =
                    runBlocking {
                        this@module.dependencies.resolve()
                    }
                forespoerselListener.onOppdateringChanged(enabled)
            }
        }
    }

    securityConfig()
    routingConfig(applicationState)

    val forespoerselListener: ForespoerselListener by dependencies
    forespoerselListener.start()

    if (PropertiesConfig.SchedulerProperties().enabled) {
        val bestillingService: BestillingService by dependencies
        val bestillingBatchService: BestillingBatchService by dependencies
        val utsendingService: UtsendingService by dependencies
        val konverteringService: KonverteringService by dependencies
        val scheduledTaskService = ScheduledTaskService(DatabaseConfig.dataSourceScheduler)
        val metricsService: MetricsService by dependencies
        val forespoerselService: ForespoerselService by dependencies
        val dataSource: DataSource by dependencies

        JobTaskConfig
            .scheduler(
                bestillingService = bestillingService,
                bestillingBatchService = bestillingBatchService,
                utsendingService = utsendingService,
                konverteringService = konverteringService,
                scheduledTaskService = scheduledTaskService,
                metricsService = metricsService,
                forespoerselService = forespoerselService,
                dataSource = dataSource,
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
