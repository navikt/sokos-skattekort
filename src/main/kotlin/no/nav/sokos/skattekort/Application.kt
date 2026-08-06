package no.nav.sokos.skattekort

import kotlin.onFailure
import kotlinx.coroutines.runBlocking

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
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
import no.nav.sokos.skattekort.config.loadEnvironmentConfig
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.infrastructure.MetricsService
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.infrastructure.pdl.PdlService
import no.nav.sokos.skattekort.infrastructure.skatteetaten.SkatteetatenClient
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.person.PersonService
import no.nav.sokos.skattekort.person.kafka.IdentifikatorEndringService
import no.nav.sokos.skattekort.person.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekortbestilling.status.StatusService
import no.nav.sokos.skattekort.skattekortdata.SkattekortDataService
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.util.launchBackgroundTask
import no.nav.sokos.skattekort.utsending.UtsendingService
import no.nav.sokos.skattekort.utsending.mq.JmsProducerService

const val FORESPORSEL_QUEUE = "forespoerselQueue"
const val FORESPORSEL_BOQ_QUEUE = "forespoerselBoqQueue"
const val LEVERANSEKOE_OPPDRAG_Z_SKATTEKORT = "leveransekoeOppdragZSkattekort"
const val LEVERANSEKOE_OPPDRAG_Z_SKATTEKORT_STOR = "leveransekoeOppdragZSkattekortStor"
const val PDL_URL = "pdlUrl"
const val PDL_AZURED_TOKEN_CLIENT = "pdlAzuredTokenClient"
const val TILGANGSMASKIN_URL = "tilgangsmaskinUrl"
const val TILGANGSMAKSIN_AZURED_TOKEN_CLIENT = "tilgangsmaksinAzuredTokenClient"
const val SKATTEETATEN_URL = "skatteetatenUrl"
const val DAREPOC_URL = "darePocUrl"
const val DAREPOC_AZURED_TOKEN_CLIENT = "darePocAzuredTokenClient"

fun main() {
    embeddedServer(Netty, port = 8080, module = Application::module).start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(applicationConfig: ApplicationConfig = environment.config) {
    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)
    commonConfig()

    PropertiesConfig.load(loadEnvironmentConfig())
    val applicationProperties = PropertiesConfig.applicationProperties
    logger.info { "Application started with environment: ${applicationProperties.profile}" }
    DatabaseConfig.migrate()

    dependencies {
        provide { createHttpClient() } cleanup { client ->
            client.close()
        }
        provide { DatabaseConfig.dataSource }
        provide { KafkaConfig() }
        provide { PropertiesConfig.unleashProperties }
        provide { PropertiesConfig.applicationProperties }
        provide(MaskinportenTokenClient::class)
        provide(AuditLogger::class)

        provide { MQConfig.connectionFactory }
        provide<Queue>(name = FORESPORSEL_QUEUE) {
            MQQueue(PropertiesConfig.mqProperties.fraForSystemQueue)
        }
        provide<Queue>(name = FORESPORSEL_BOQ_QUEUE) {
            MQQueue("${PropertiesConfig.mqProperties.fraForSystemQueue}_BOQ")
        }
        provide<Queue>(name = LEVERANSEKOE_OPPDRAG_Z_SKATTEKORT) {
            val queue = MQQueue(PropertiesConfig.mqProperties.leveransekoeOppdragZSkattekort)
            queue.messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
            queue
        }
        provide<Queue>(name = LEVERANSEKOE_OPPDRAG_Z_SKATTEKORT_STOR) {
            val queue = MQQueue(PropertiesConfig.mqProperties.leveransekoeOppdragZSkattekortStor)
            queue.messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
            queue
        }
        provide<String>(name = PDL_URL) { PropertiesConfig.pdlProperties.pdlUrl }
        provide<AzuredTokenClient>(name = PDL_AZURED_TOKEN_CLIENT) {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.pdlProperties.pdlScope)
        }
        provide<String>(name = TILGANGSMASKIN_URL) { PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinUrl }
        provide<AzuredTokenClient>(name = TILGANGSMAKSIN_AZURED_TOKEN_CLIENT) {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinScope)
        }
        provide<String>(name = SKATTEETATEN_URL) { PropertiesConfig.skatteetatenProperties.skatteetatenUrl }
        provide<String>(name = DAREPOC_URL) { PropertiesConfig.darePocProperties.darePocUrl }
        provide<AzuredTokenClient>(name = DAREPOC_AZURED_TOKEN_CLIENT) {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.darePocProperties.darePocScope)
        }
        provide(StatusService::class)
        provide(PersonService::class)
        provide(ForespoerselService::class)
        provide(ForespoerselListener::class)
        provide(UtsendingService::class)
        provide(BestillingsbatchService::class)
        provide(BestillingService::class)
        provide(SkattekortDataService::class)
        provide(SkatteetatenClient::class)
        provide(SkattekortService::class)
        provide(KafkaConsumerService::class)
        provide(PdlClientService::class)
        provide(PdlService::class)
        provide(TilgangsmaskinClientService::class)
        provide(IdentifikatorEndringService::class)
        provide(MetricsService::class)
        provide(JmsProducerService::class)
        // SOKOS-DARE-POC skal kun brukes i test.
        if (!PropertiesConfig.isProd) {
            provide(UtsendingDareClientService::class)
        } else {
            provide<UtsendingDareClientService?> { null }
        }
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

    val unleashIntegration: UnleashIntegration by dependencies
    val forespoerselListener: ForespoerselListener by dependencies
    forespoerselListener.onOppdateringChanged(unleashIntegration.isForespoerselListenerEnabled())

    val scheduler =
        PropertiesConfig.schedulerProperties.takeIf { it.enabled }?.let {
            val bestillingService: BestillingService by dependencies
            val bestillingsbatchService: BestillingsbatchService by dependencies
            val utsendingService: UtsendingService by dependencies
            val skattekortdataService: SkattekortDataService by dependencies
            val metricsService: MetricsService by dependencies
            val skattekortService: SkattekortService by dependencies

            JobTaskConfig
                .scheduler(
                    bestillingService = bestillingService,
                    bestillingsbatchService = bestillingsbatchService,
                    utsendingService = utsendingService,
                    skattekortdataService = skattekortdataService,
                    metricsService = metricsService,
                    skattekortService = skattekortService,
                    dataSource = DatabaseConfig.dataSourceScheduler,
                ).also { it.start() }
        }

    val kafkaConsumerService =
        PropertiesConfig.kafkaProperties.takeIf { it.enabled }?.let {
            val kafkaConsumerService: KafkaConsumerService by dependencies
            applicationState.onReady = {
                launchBackgroundTask(applicationState) {
                    kafkaConsumerService.start(applicationState)
                }
            }
            kafkaConsumerService
        }

    monitor.subscribe(ApplicationStopPreparing) {
        logger.info { "Application stopping - shutting down background services" }

        // Step 1: Stop Kafka consumer first
        kafkaConsumerService?.let { service ->
            logger.info { "Stopping Kafka consumer..." }
            service.close()
        }

        // Step 2: Stop db-scheduler
        scheduler?.let { service ->
            if (!service.schedulerState.isShuttingDown) {
                logger.info { "Stopping scheduler..." }
                scheduler.stop()
            }
        }

        // Step 3: Stop ForespoerselListener (which uses MQ/JMS)
        logger.info { "Stopping ForespoerselListener..." }
        forespoerselListener.onOppdateringChanged(false)
    }

    monitor.subscribe(ApplicationStopped) {
        logger.info { "Application stopped - closing database pools" }

        // Only close datasources after all services have stopped
        if (!(PropertiesConfig.isLocal || PropertiesConfig.isTest)) {
            logger.info { "Closing database scheduler pools..." }
            runCatching { (DatabaseConfig.dataSourceScheduler as? HikariDataSource)?.close() }
                .onFailure { logger.warn(it) { "Error closing scheduler datasource" } }

            logger.info { "Closing main database pools..." }
            runCatching { (DatabaseConfig.dataSource as? HikariDataSource)?.close() }
                .onFailure { logger.warn(it) { "Error closing main datasource" } }
        }
    }

    logger.info { "Kafka consumer is enabled: ${PropertiesConfig.kafkaProperties.enabled}" }
}
