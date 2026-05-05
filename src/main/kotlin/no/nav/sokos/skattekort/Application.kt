package no.nav.sokos.skattekort

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
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.mergeWithEnv
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.person.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.skattekort.SkattekortService
import no.nav.sokos.skattekort.skattekortbestilling.BestillingsbatchService
import no.nav.sokos.skattekort.skattekorthenting.BestillingService
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

    // Circular dependency: UnleashIntegration <-> ForespoerselListener <-> ForespoerselService
    lateinit var forespoerselListener: ForespoerselListener
    val unleashIntegration =
        UnleashIntegration { enabled ->
            forespoerselListener.onOppdateringChanged(enabled)
        }

    val forespoerselService = ForespoerselService(featureToggles = unleashIntegration)
    forespoerselListener = ForespoerselListener(forespoerselService = forespoerselService)
    val bestillingsbatchService = BestillingsbatchService(featureToggles = unleashIntegration)
    val utsendingService =
        UtsendingService(
            featureToggles = unleashIntegration,
            utsendingDareClientService = if (!PropertiesConfig.isProd) UtsendingDareClientService() else null,
        )
    val skattekortService = SkattekortService()

    forespoerselListener.onOppdateringChanged(unleashIntegration.isForespoerselListenerEnabled())

    securityConfig()
    routingConfig(
        applicationState = applicationState,
        bestillingsbatchService = bestillingsbatchService,
        forespoerselService = forespoerselService,
        skattekortService = skattekortService,
        utsendingService = utsendingService,
    )

    if (PropertiesConfig.schedulerProperties.enabled) {
        val scheduler =
            JobTaskConfig
                .scheduler(
                    bestillingService = BestillingService(featureToggles = unleashIntegration),
                    bestillingsbatchService = bestillingsbatchService,
                    utsendingService = utsendingService,
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
                KafkaConsumerService().start(applicationState)
            }
        }
    }

    logger.info { "Kafka consumer is enabled: ${kafkaProperties.enabled}" }
}
