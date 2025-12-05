package no.nav.sokos.skattekort.config

import java.io.File

import com.nimbusds.jose.jwk.RSAKey
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig

/**
 * Konfigurasjonssettinger er dokumentert i dokumentasjon/drift/konfigurasjon.md
 */
object PropertiesConfig {
    private var envConfig: HoconApplicationConfig = HoconApplicationConfig(ConfigFactory.empty())

    init {
        initEnvConfig(
            HoconApplicationConfig(
                ConfigFactory.parseMap(mapOf("APPLICATION_ENV" to "TEST")),
            ),
        )
    }

    fun initEnvConfig(applicationConfig: ApplicationConfig? = null) {
        val environment = System.getenv("APPLICATION_ENV") ?: System.getProperty("APPLICATION_ENV") ?: applicationConfig?.propertyOrNull("APPLICATION_ENV")?.getString()
        val fileConfig =
            when {
                environment == null || environment.lowercase() == "local" -> {
                    val defaultConfig = ConfigFactory.parseFile(File("defaults.properties"))
                    ConfigFactory.parseResources("application-local.conf").withFallback(defaultConfig)
                }

                else -> ConfigFactory.parseResources("application-${environment.lowercase()}.conf")
            }

        // Precedence (highest -> lowest):
        // 1. system environment
        // 2. system properties
        // 3. applicationConfig (if provided)
        // 4. fileConfig (resource/local + defaults)
        val base =
            ConfigFactory
                .systemEnvironment()
                .withFallback(ConfigFactory.systemProperties())
                .withFallback(fileConfig)

        envConfig =
            applicationConfig?.let { external ->
                val externalConfig = ConfigFactory.parseMap(external.toMap())
                HoconApplicationConfig(base.withFallback(externalConfig).resolve())
            } ?: HoconApplicationConfig(base.resolve())
    }

    fun getOrEmpty(key: String): String = envConfig.propertyOrNull(key)?.getString() ?: ""

    fun get(key: String): String = envConfig.property(key).getString()

    fun getApplicationProperties(): ApplicationProperties =
        ApplicationProperties(
            naisAppName = getOrEmpty("NAIS_APP_NAME"),
            gyldigeFnr = getOrEmpty("GYLDIGE_FNR"),
            environment = Environment.valueOf(getOrEmpty("ENVIRONMENT")),
            mqListenerEnabled = getOrEmpty("MQ_LISTENER_ENABLED").toBoolean(),
            podName = getOrEmpty("NAIS_POD_NAME"),
            bestillingOrgnr = get("BESTILLING_ORGNR"),
        )

    fun getPostgresProperties(): PostgresProperties {
        val postgresProperties =
            PostgresProperties(
                jdbcUrl = getOrEmpty("DB_JDBC_URL"),
                name = get("DB_DATABASE"),
                host = getOrEmpty("DB_HOST"),
                port = getOrEmpty("DB_PORT"),
                username = get("DB_USERNAME"),
                password = get("DB_PASSWORD"),
            )
        return postgresProperties
    }

    fun getMQProperties(): MQProperties =
        MQProperties(
            hostname = getOrEmpty("MQ_HOSTNAME"),
            port = getOrEmpty("MQ_PORT").ifBlank { "0" }.toInt(),
            mqQueueManagerName = getOrEmpty("MQ_QUEUE_MANAGER_NAME"),
            mqChannelName = getOrEmpty("MQ_CHANNEL_NAME"),
            serviceUsername = getOrEmpty("MQ_SERVICE_USERNAME"),
            servicePassword = getOrEmpty("MQ_SERVICE_PASSWORD"),
            userAuth = true,
            fraForSystemQueue = get("MQ_FRA_FORSYSTEM_QUEUE_NAME"),
            leveransekoeOppdragZSkattekort = get("MQ_LEVERANSEKOE_OPPDRAGZ_SKATTEKORT"),
        )

    fun getMaskinportenProperties(): MaskinportenProperties =
        MaskinportenProperties(
            clientId = getOrEmpty("MASKINPORTEN_CLIENT_ID"),
            wellKnownUrl = getOrEmpty("MASKINPORTEN_WELL_KNOWN_URL"),
            rsaKey = RSAKey.parse(getOrEmpty("MASKINPORTEN_CLIENT_JWK")),
            scopes = getOrEmpty("MASKINPORTEN_SCOPES"),
            systemBrukerClaim = getOrEmpty("MASKINPORTEN_SYSTEM_BRUKER_CLAIM"),
        )

    fun getSkatteetatenProperties(): SkatteetatenProperties =
        SkatteetatenProperties(
            skatteetatenApiUrl = getOrEmpty("SKATTEETATEN_API_URL"),
        )

    fun getKafkaProperties(): KafkaProperties =
        KafkaProperties(
            enabled = getOrEmpty("KAFKA_CONSUMER_ENABLED").toBoolean(),
            topic = getOrEmpty("KAFKA_CONSUMER_TOPIC"),
            consumerGroupId = getOrEmpty("KAFKA_CONSUMER_GROUP_ID"),
            offsetReset = getOrEmpty("KAFKA_CONSUMER_OFFSET_RESET"),
            brokers = getOrEmpty("KAFKA_BROKERS"),
            schemaRegistry = getOrEmpty("KAFKA_SCHEMA_REGISTRY"),
            schemaRegistryUser = getOrEmpty("KAFKA_SCHEMA_REGISTRY_USER"),
            schemaRegistryPassword = getOrEmpty("KAFKA_SCHEMA_REGISTRY_PASSWORD"),
            truststorePath = getOrEmpty("KAFKA_TRUSTSTORE_PATH"),
            credstorePassword = getOrEmpty("KAFKA_CREDSTORE_PASSWORD"),
            keystorePath = getOrEmpty("KAFKA_KEYSTORE_PATH"),
        )

    fun getPdlProperties(): PdlProperties =
        PdlProperties(
            pdlUrl = getOrEmpty("PDL_URL"),
            pdlScope = getOrEmpty("PDL_SCOPE"),
        )

    fun getUnleashProperties(): UnleashProperties =
        UnleashProperties(
            unleashAPI = getOrEmpty("UNLEASH_SERVER_API_URL"),
            apiKey = getOrEmpty("UNLEASH_SERVER_API_TOKEN"),
            environment = getOrEmpty("UNLEASH_SERVER_API_ENV"),
        )

    data class AzureAdProperties(
        val clientId: String = getOrEmpty("AZURE_APP_CLIENT_ID"),
        val wellKnownUrl: String = getOrEmpty("AZURE_APP_WELL_KNOWN_URL"),
        val tenantId: String = getOrEmpty("AZURE_APP_TENANT_ID"),
        val clientSecret: String = getOrEmpty("AZURE_APP_CLIENT_SECRET"),
        val providerName: String = get("AZURE_APP_AUTH_PROVIDER_NAME"),
    )

    data class ApplicationProperties(
        val naisAppName: String,
        val environment: Environment,
        val mqListenerEnabled: Boolean,
        val gyldigeFnr: String,
        val podName: String,
        val bestillingOrgnr: String,
    )

    data class PostgresProperties(
        val jdbcUrl: String,
        val name: String,
        val host: String,
        val port: String,
        val username: String,
        val password: String,
    )

    data class MQProperties(
        val hostname: String,
        val port: Int,
        val mqQueueManagerName: String,
        val mqChannelName: String,
        val serviceUsername: String,
        val servicePassword: String,
        val userAuth: Boolean = true,
        val fraForSystemQueue: String,
        val leveransekoeOppdragZSkattekort: String,
    )

    data class SchedulerProperties(
        val enabled: Boolean = getOrEmpty("SCHEDULER_ENABLED").toBoolean(),
        val cronBestilling: String = get("SEND_BESTILLING_BATCH_CRON_EXPRESSION"),
        val cronUtsending: String = get("SEND_UTSENDING_CRON_EXPRESSION"),
        val cronHenting: String = get("HENT_SKATTEKORT_BATCH_CRON_EXPRESSION"),
        val cronHentOppdaterte: String = get("HENT_OPPDATERTE_SKATTEKORT_BATCH_CRON_EXPRESSION"),
        val cronFetchMetrics: String = get("FETCH_METRICS_CRON_EXPRESSION"),
    )

    data class MaskinportenProperties(
        val clientId: String,
        val wellKnownUrl: String,
        val rsaKey: RSAKey?,
        val scopes: String,
        val systemBrukerClaim: String,
    )

    data class SkatteetatenProperties(
        val skatteetatenApiUrl: String,
    )

    data class KafkaProperties(
        val enabled: Boolean,
        val topic: String,
        val consumerGroupId: String,
        val offsetReset: String,
        val brokers: String,
        val schemaRegistry: String,
        val schemaRegistryUser: String,
        val schemaRegistryPassword: String,
        val truststorePath: String,
        val credstorePassword: String,
        val keystorePath: String,
    )

    data class PdlProperties(
        val pdlUrl: String,
        val pdlScope: String,
    )

    data class UnleashProperties(
        val unleashAPI: String,
        val apiKey: String,
        val environment: String,
    )

    enum class Environment {
        LOCAL,
        TEST,
        DEV,
        PROD,
    }

    fun isLocal() = getApplicationProperties().environment == Environment.LOCAL

    fun isTest() = getApplicationProperties().environment == Environment.TEST
}
