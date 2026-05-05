package no.nav.sokos.skattekort

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
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

    // Infrastructure
    val httpClient = createHttpClient()
    val dataSource = DatabaseConfig.dataSource
    val kafkaConfig = KafkaConfig()
    val connectionFactory = MQConfig.connectionFactory
    val auditLogger = AuditLogger()

    // MQ Queues
    val forespoerselQueue = MQQueue(PropertiesConfig.mqProperties.fraForSystemQueue)
    val forespoerselBoqQueue = MQQueue("${PropertiesConfig.mqProperties.fraForSystemQueue}_BOQ")
    val leveransekoeOppdragZSkattekort =
        MQQueue(PropertiesConfig.mqProperties.leveransekoeOppdragZSkattekort).apply {
            messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
        }
    val leveransekoeOppdragZSkattekortStor =
        MQQueue(PropertiesConfig.mqProperties.leveransekoeOppdragZSkattekortStor).apply {
            messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
        }

    // Token clients
    val maskinportenTokenClient = MaskinportenTokenClient(httpClient)
    val pdlAzuredTokenClient = AzuredTokenClient(httpClient, PropertiesConfig.pdlProperties.pdlScope)
    val tilgangsmaskinAzuredTokenClient = AzuredTokenClient(httpClient, PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinScope)
    val darePocAzuredTokenClient = AzuredTokenClient(httpClient, PropertiesConfig.darePocProperties.darePocScope)

    // Infrastructure clients
    val pdlClientService = PdlClientService(httpClient, PropertiesConfig.pdlProperties.pdlUrl, pdlAzuredTokenClient)
    val tilgangsmaskinClientService = TilgangsmaskinClientService(httpClient, PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinUrl, tilgangsmaskinAzuredTokenClient)
    val skatteetatenClient = SkatteetatenClient(httpClient, PropertiesConfig.skatteetatenProperties.skatteetatenUrl, maskinportenTokenClient)
    val utsendingDareClientService: UtsendingDareClientService? =
        if (!PropertiesConfig.isProd) {
            UtsendingDareClientService(httpClient, PropertiesConfig.darePocProperties.darePocUrl, darePocAzuredTokenClient)
        } else {
            null
        }

    // Domain services
    val personService = PersonService(dataSource, pdlClientService)
    val pdlService = PdlService(pdlClientService, tilgangsmaskinClientService, auditLogger)
    val skattekortDataService = SkattekortDataService(dataSource)
    val statusService = StatusService(dataSource)
    val metricsService = MetricsService(dataSource)
    val skattekortService = SkattekortService(dataSource, personService, tilgangsmaskinClientService, auditLogger)

    // Circular dependency: UnleashIntegration <-> ForespoerselListener <-> ForespoerselService
    lateinit var forespoerselListener: ForespoerselListener
    val unleashIntegration =
        UnleashIntegration { enabled ->
            forespoerselListener.onOppdateringChanged(enabled)
        }

    val forespoerselService = ForespoerselService(dataSource, personService, unleashIntegration)
    forespoerselListener = ForespoerselListener(connectionFactory, forespoerselService, forespoerselQueue, forespoerselBoqQueue)
    val bestillingsbatchService = BestillingsbatchService(dataSource, skatteetatenClient, unleashIntegration)
    val bestillingService = BestillingService(dataSource, skatteetatenClient, unleashIntegration)
    val utsendingService = UtsendingService(dataSource, connectionFactory, leveransekoeOppdragZSkattekort, leveransekoeOppdragZSkattekortStor, unleashIntegration, utsendingDareClientService)

    val identifikatorEndringService = IdentifikatorEndringService(dataSource, pdlClientService, personService)
    val kafkaConsumerService = KafkaConsumerService(kafkaConfig, identifikatorEndringService)

    forespoerselListener.onOppdateringChanged(unleashIntegration.isForespoerselListenerEnabled())

    securityConfig()
    routingConfig(
        applicationState = applicationState,
        bestillingsbatchService = bestillingsbatchService,
        forespoerselService = forespoerselService,
        pdlService = pdlService,
        personService = personService,
        skattekortService = skattekortService,
        statusService = statusService,
        utsendingService = utsendingService,
    )

    if (PropertiesConfig.schedulerProperties.enabled) {
        val scheduler =
            JobTaskConfig
                .scheduler(
                    bestillingService = bestillingService,
                    bestillingsbatchService = bestillingsbatchService,
                    utsendingService = utsendingService,
                    skattekortdataService = skattekortDataService,
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
            launchBackgroundTask(applicationState) {
                kafkaConsumerService.start(applicationState)
            }
        }
    }

    logger.info { "Kafka consumer is enabled: ${kafkaProperties.enabled}" }
}
