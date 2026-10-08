package no.nav.sokos.skattekort.config

import com.ibm.mq.jakarta.jms.MQQueue
import com.ibm.msg.client.jakarta.wmq.WMQConstants
import io.ktor.client.HttpClient
import io.ktor.util.AttributeKey
import jakarta.jms.ConnectionFactory
import jakarta.jms.Queue

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
import no.nav.sokos.skattekort.util.BackgroundTaskRunner
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.utsending.UtsendingService
import no.nav.sokos.skattekort.utsending.mq.JmsProducerService

class ApiServices(
    val bestillingsbatchService: BestillingsbatchService,
    val personService: PersonService,
    val utsendingService: UtsendingService,
    val skattekortService: SkattekortService,
    val forespoerselService: ForespoerselService,
    val statusService: StatusService,
    val backgroundTaskRunner: BackgroundTaskRunner,
    val pdlService: PdlService,
) {
    companion object {
        val key = AttributeKey<ApiServices>("apiServices")
    }
}

data class ApplicationServiceOverrides(
    val pdlUrl: String? = null,
    val tilgangsmaskinUrl: String? = null,
    val skatteetatenUrl: String? = null,
    val darePocUrl: String? = null,
    val pdlTokenClient: AzuredTokenClient? = null,
    val tilgangsmaskinTokenClient: AzuredTokenClient? = null,
    val darePocTokenClient: AzuredTokenClient? = null,
    val maskinportenTokenClient: MaskinportenTokenClient? = null,
    val connectionFactory: ConnectionFactory? = null,
    val forespoerselQueue: Queue? = null,
    val forespoerselBoqQueue: Queue? = null,
    val utsendingQueue: Queue? = null,
    val utsendingStorQueue: Queue? = null,
)

class ApplicationServices(
    applicationState: ApplicationState,
    overrides: ApplicationServiceOverrides = ApplicationServiceOverrides(),
) {
    val httpClient: HttpClient = createHttpClient()
    val dataSource = DatabaseConfig.dataSource
    val kafkaConfig = KafkaConfig()
    val backgroundTaskRunner = BackgroundTaskRunner(applicationState)
    private val auditLogger = AuditLogger()

    private val mqProperties = PropertiesConfig.mqProperties
    private val connectionFactory = overrides.connectionFactory ?: MQConfig.connectionFactory
    private val pdlUrl = overrides.pdlUrl ?: PropertiesConfig.pdlProperties.pdlUrl
    private val tilgangsmaskinUrl =
        overrides.tilgangsmaskinUrl ?: PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinUrl
    private val skatteetatenUrl =
        overrides.skatteetatenUrl ?: PropertiesConfig.skatteetatenProperties.skatteetatenUrl
    private val darePocUrl = overrides.darePocUrl ?: PropertiesConfig.darePocProperties.darePocUrl
    private val pdlTokenClient =
        overrides.pdlTokenClient
            ?: AzuredTokenClient(httpClient, PropertiesConfig.pdlProperties.pdlScope)
    private val tilgangsmaskinTokenClient =
        overrides.tilgangsmaskinTokenClient
            ?: AzuredTokenClient(
                httpClient,
                PropertiesConfig.tilgangsmaskinProperties.tilgangsmaskinScope,
            )
    private val darePocTokenClient =
        overrides.darePocTokenClient
            ?: AzuredTokenClient(
                httpClient,
                PropertiesConfig.darePocProperties.darePocScope,
            )

    private fun queue(
        name: String,
        bodyStyle: Int? = null,
    ): Queue =
        MQQueue(name).apply {
            bodyStyle?.let { messageBodyStyle = it }
        }

    private val forespoerselQueue = overrides.forespoerselQueue ?: queue(mqProperties.fraForSystemQueue)
    private val forespoerselBoqQueue = overrides.forespoerselBoqQueue ?: queue("${mqProperties.fraForSystemQueue}_BOQ")
    private val utsendingQueue =
        overrides.utsendingQueue
            ?: queue(mqProperties.leveransekoeOppdragZSkattekort, WMQConstants.WMQ_MESSAGE_BODY_MQ)
    private val utsendingStorQueue =
        overrides.utsendingStorQueue
            ?: queue(mqProperties.leveransekoeOppdragZSkattekortStor, WMQConstants.WMQ_MESSAGE_BODY_MQ)

    val pdlClientService = PdlClientService(httpClient, pdlUrl, pdlTokenClient)
    val tilgangsmaskinClientService =
        TilgangsmaskinClientService(httpClient, tilgangsmaskinUrl, tilgangsmaskinTokenClient)
    val dareClientService =
        if (PropertiesConfig.isProd) {
            null
        } else {
            UtsendingDareClientService(httpClient, darePocUrl, darePocTokenClient)
        }
    val skatteetatenClient =
        SkatteetatenClient(
            httpClient,
            skatteetatenUrl,
            overrides.maskinportenTokenClient ?: MaskinportenTokenClient(httpClient),
        )
    val personService =
        PersonService(dataSource, pdlClientService, tilgangsmaskinClientService)
    val forespoerselService = ForespoerselService(dataSource, personService)
    val forespoerselListener =
        ForespoerselListener(
            connectionFactory,
            forespoerselService,
            forespoerselQueue,
            forespoerselBoqQueue,
        )
    val unleashIntegration =
        UnleashIntegration(forespoerselListener::onOppdateringChanged)
    val bestillingsbatchService =
        BestillingsbatchService(dataSource, skatteetatenClient, unleashIntegration)
    val bestillingService =
        BestillingService(dataSource, skatteetatenClient, unleashIntegration)
    val jmsProducerService = JmsProducerService(connectionFactory)
    val utsendingService =
        UtsendingService(
            dataSource,
            jmsProducerService,
            utsendingQueue,
            utsendingStorQueue,
            unleashIntegration,
            dareClientService,
        )
    val skattekortDataService = SkattekortDataService(dataSource)
    val skattekortService =
        SkattekortService(
            dataSource,
            personService,
            tilgangsmaskinClientService,
            auditLogger,
        )
    val pdlService =
        PdlService(pdlClientService, tilgangsmaskinClientService, auditLogger)
    val statusService = StatusService(dataSource, tilgangsmaskinClientService)
    val apiServices =
        ApiServices(
            bestillingsbatchService = bestillingsbatchService,
            personService = personService,
            utsendingService = utsendingService,
            skattekortService = skattekortService,
            forespoerselService = forespoerselService,
            statusService = statusService,
            backgroundTaskRunner = backgroundTaskRunner,
            pdlService = pdlService,
        )
    val metricsService = MetricsService(dataSource)
    val identifikatorEndringService =
        IdentifikatorEndringService(dataSource, pdlClientService, personService)
    val kafkaConsumerService by lazy {
        KafkaConsumerService(kafkaConfig, identifikatorEndringService)
    }
}
