package no.nav.sokos.skattekort

import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.ApiServices
import no.nav.sokos.skattekort.config.ApplicationServiceOverrides
import no.nav.sokos.skattekort.config.ApplicationServices
import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.DatabaseConfig
import no.nav.sokos.skattekort.config.JobTaskConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.loadEnvironmentConfig
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig

fun main() {
    embeddedServer(Netty, port = 8080) {
        module()
    }.start(true)
}

private val logger = KotlinLogging.logger {}

fun Application.module(
    applicationConfig: ApplicationConfig = environment.config,
    serviceOverrides: ApplicationServiceOverrides = ApplicationServiceOverrides(),
) {
    val applicationState = ApplicationState()
    applicationLifecycleConfig(applicationState)
    commonConfig()

    PropertiesConfig.load(applicationConfig.loadEnvironmentConfig())
    val applicationProperties = PropertiesConfig.applicationProperties
    logger.info { "Application started with environment: ${applicationProperties.profile}" }
    DatabaseConfig.migrate()

    val services = ApplicationServices(applicationState, serviceOverrides)
    attributes.put(ApiServices.key, services.apiServices)

    securityConfig()
    routingConfig(applicationState, services.apiServices)

    services.forespoerselListener.onOppdateringChanged(services.unleashIntegration.isForespoerselListenerEnabled())

    val scheduler =
        PropertiesConfig.schedulerProperties.takeIf { it.enabled }?.let {
            JobTaskConfig
                .scheduler(
                    bestillingService = services.bestillingService,
                    bestillingsbatchService = services.bestillingsbatchService,
                    utsendingService = services.utsendingService,
                    skattekortdataService = services.skattekortDataService,
                    metricsService = services.metricsService,
                    skattekortService = services.skattekortService,
                    dataSource = DatabaseConfig.dataSourceScheduler,
                ).also { it.start() }
        }

    val kafkaConsumerService =
        PropertiesConfig.kafkaProperties.takeIf { it.enabled }?.let {
            applicationState.onReady = {
                services.backgroundTaskRunner.launch {
                    services.kafkaConsumerService.start(applicationState)
                }
            }
            services.kafkaConsumerService
        }

    monitor.subscribe(ApplicationStopPreparing) {
        logger.info { "Application stopping - shutting down background services" }

        kafkaConsumerService?.let { service ->
            logger.info { "Stopping Kafka consumer..." }
            service.close()
        }

        scheduler?.let { service ->
            if (!service.schedulerState.isShuttingDown) {
                logger.info { "Stopping scheduler..." }
                scheduler.stop()
            }
        }

        logger.info { "Stopping ForespoerselListener..." }
        services.forespoerselListener.onOppdateringChanged(false)

        logger.info { "Stopping background tasks..." }
        services.backgroundTaskRunner.close()
    }

    monitor.subscribe(ApplicationStopped) {
        logger.info { "Application stopped - closing database pools" }
        services.httpClient.close()

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
