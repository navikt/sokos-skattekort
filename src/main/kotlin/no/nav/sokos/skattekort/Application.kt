package no.nav.sokos.skattekort

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
import no.nav.sokos.skattekort.config.mergeWithEnv
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
import no.nav.sokos.skattekort.skattekortbestilling.StatusService
import no.nav.sokos.skattekort.skattekortdata.SkattekortDataService
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.util.launchBackgroundTask
import no.nav.sokos.skattekort.utsending.UtsendingService

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

    PropertiesConfig.load(applicationConfig.mergeWithEnv())
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

    if (PropertiesConfig.schedulerProperties.enabled) {
        val bestillingService: BestillingService by dependencies
        val bestillingsbatchService: BestillingsbatchService by dependencies
        val utsendingService: UtsendingService by dependencies
        val skattekortdataService: SkattekortDataService by dependencies
        val metricsService: MetricsService by dependencies
        val forespoerselService: ForespoerselService by dependencies
        val skattekortService: SkattekortService by dependencies

        val scheduler =
            JobTaskConfig
                .scheduler(
                    bestillingService = bestillingService,
                    bestillingsbatchService = bestillingsbatchService,
                    utsendingService = utsendingService,
                    skattekortdataService = skattekortdataService,
                    metricsService = metricsService,
                    forespoerselService = forespoerselService,
                    skattekortService = skattekortService,
                    dataSource = DatabaseConfig.dataSourceScheduler,
                ).also { it.start() }

        monitor.subscribe(ApplicationStopPreparing) {
            if (!scheduler.schedulerState.isShuttingDown) {
                logger.info { "Stopping scheduler..." }
                scheduler.stop()
            }
        }

        if (!(PropertiesConfig.isLocal || PropertiesConfig.isTest)) {
            monitor.subscribe(ApplicationStopped) {
                logger.info { "Closing database scheduler pools..." }
                (DatabaseConfig.dataSourceScheduler as? HikariDataSource)?.close()
            }
        }
    }

    val kafkaProperties = PropertiesConfig.kafkaProperties
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
