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

import no.nav.sokos.skattekort.config.ApplicationState
import no.nav.sokos.skattekort.config.KafkaConfig
import no.nav.sokos.skattekort.config.MQConfig
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.config.applicationLifecycleConfig
import no.nav.sokos.skattekort.config.commonConfig
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.config.routingConfig
import no.nav.sokos.skattekort.config.securityConfig
import no.nav.sokos.skattekort.kafka.KafkaConsumerService
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.sokos.skattekort.security.MaskinportenTokenClient
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

    // DatabaseConfig.migrate()

    dependencies {
        provide { createHttpClient() }
        // provide { DatabaseConfig.dataSource }
        provide { KafkaConfig() }
        provide { PropertiesConfig.getUnleashProperties() }
        provide { PropertiesConfig.getApplicationProperties() }
        provide(MaskinportenTokenClient::class)
        provide { MQConfig.connectionFactory }
        provide<String>(name = "pdlUrl") { PropertiesConfig.getPdlProperties().pdlUrl }
        provide<Queue>(name = "forespoerselQueue") {
            MQQueue(PropertiesConfig.getMQProperties().fraForSystemQueue)
        }
        provide<Queue>(name = "leveransekoeOppdragZSkattekort") {
            val queue = MQQueue(PropertiesConfig.getMQProperties().leveransekoeOppdragZSkattekort)
            queue.messageBodyStyle = WMQConstants.WMQ_MESSAGE_BODY_MQ
            queue
        }
        provide<AzuredTokenClient>(name = "pdlAzuredTokenClient") {
            AzuredTokenClient(createHttpClient(), PropertiesConfig.getPdlProperties().pdlScope)
        }
//        provide(UnleashIntegration::class)
//
//        provide(PersonService::class)
//        provide(ForespoerselService::class)
//        provide(ForespoerselListener::class)
//        provide(UtsendingService::class)
//        provide(BestillingService::class)
//        provide(SkatteetatenClient::class)
//        provide(ScheduledTaskService::class)
//        provide(SkattekortPersonService::class)
//        provide(KafkaConsumerService::class)
//        provide(PdlClientService::class)
//        provide(IdentifikatorEndringService::class)
//        provide(MetricsService::class)
    }

    commonConfig()
    securityConfig(useAuthentication)
    routingConfig(useAuthentication, applicationState)

//    val forespoerselListener: ForespoerselListener by dependencies
//    forespoerselListener.start()

    if (PropertiesConfig.SchedulerProperties().enabled) {
//        val bestillingService: BestillingService by dependencies
//        val utsendingService: UtsendingService by dependencies
//        val scheduledTaskService: ScheduledTaskService by dependencies
//        val metricsService: MetricsService by dependencies
//        val dataSource: DataSource by dependencies
//        JobTaskConfig
//            .scheduler(
//                bestillingService,
//                utsendingService,
//                scheduledTaskService,
//                metricsService,
//                dataSource,
//            ).start()
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
