package no.nav.sokos.skattekort.config

import java.io.File

import com.nimbusds.jose.jwk.RSAKey
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig

object PropertiesConfig {
    private var envConfig: HoconApplicationConfig = HoconApplicationConfig(ConfigFactory.empty())

    fun initEnvConfig(applicationConfig: ApplicationConfig? = null) {
        val environment = System.getenv("APPLICATION_ENV") ?: System.getProperty("APPLICATION_ENV")
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
        // 3. fileConfig (resource/local + defaults)
        // 4. applicationConfig (if provided)
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
            environment = Environment.valueOf(getOrEmpty("ENVIRONMENT")),
            useAuthentication = getOrEmpty("USE_AUTHENTICATION").toBoolean(),
            mqListenerEnabled = getOrEmpty("MQ_LISTENER_ENABLED").toBoolean(),
        )

    fun getPostgresProperties(): PostgresProperties =
        PostgresProperties(
            name = getOrEmpty("POSTGRES_NAME"),
            host = getOrEmpty("POSTGRES_HOST"),
            port = getOrEmpty("POSTGRES_PORT"),
            username = getOrEmpty("POSTGRES_USER_USERNAME"),
            password = getOrEmpty("POSTGRES_USER_PASSWORD"),
            adminUsername = getOrEmpty("POSTGRES_ADMIN_USERNAME"),
            adminPassword = getOrEmpty("POSTGRES_ADMIN_PASSWORD"),
            adminRole = "${get("POSTGRES_NAME")}-admin",
            userRole = "${getOrEmpty("POSTGRES_NAME")}-user",
            vaultMountPath = getOrEmpty("VAULT_MOUNTPATH"),
        )

    fun getMQProperties(): MQProperties =
        MQProperties(
            hostname = getOrEmpty("MQ_HOSTNAME"),
            port = getOrEmpty("MQ_PORT").ifBlank { "0" }.toInt(),
            mqQueueManagerName = getOrEmpty("MQ_QUEUE_MANAGER_NAME"),
            mqChannelName = getOrEmpty("MQ_CHANNEL_NAME"),
            serviceUsername = getOrEmpty("MQ_SERVICE_USERNAME"),
            servicePassword = getOrEmpty("MQ_SERVICE_PASSWORD"),
            userAuth = true,
            fraForSystemQueue = get("MQ_FRA_FORSYSTEM_ALT_QUEUE_NAME"),
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

    fun getSftpProperties(): SftpProperties =
        SftpProperties(
            host = get("SFTP_HOST"),
            port = get("SFTP_PORT").toInt(),
            user = getOrEmpty("SFTP_USER"),
            privateKey = getOrEmpty("SFTP_PRIVATE_KEY"),
            keyPassword = getOrEmpty("SFTP_KEY_PASSWORD"),
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
            useSSLSecurity = getOrEmpty("KAFKA_USE_SSL_SECURITY").toBoolean(),
            truststorePath = getOrEmpty("KAFKA_TRUSTSTORE_PATH"),
            credstorePassword = getOrEmpty("KAFKA_CREDSTORE_PASSWORD"),
            keystorePath = getOrEmpty("KAFKA_KEYSTORE_PATH"),
        )

    fun getPdlProperties(): PdlProperties =
        PdlProperties(
            pdlUrl = getOrEmpty("PDL_URL"),
            pdlScope = getOrEmpty("PDL_SCOPE"),
        )

    data class AzureAdProperties(
        val clientId: String = getOrEmpty("AZURE_APP_CLIENT_ID"),
        val wellKnownUrl: String = getOrEmpty("AZURE_APP_WELL_KNOWN_URL"),
        val tenantId: String = getOrEmpty("AZURE_APP_TENANT_ID"),
        val clientSecret: String = getOrEmpty("AZURE_APP_CLIENT_SECRET"),
    )

    data class ApplicationProperties(
        val naisAppName: String,
        val environment: Environment,
        val useAuthentication: Boolean,
        val mqListenerEnabled: Boolean,
    )

    data class PostgresProperties(
        val name: String,
        val host: String,
        val port: String,
        val username: String,
        val password: String,
        val adminUsername: String,
        val adminPassword: String,
        val adminRole: String,
        val userRole: String,
        val vaultMountPath: String,
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

    data class SftpProperties(
        val host: String,
        val port: Int,
        val user: String,
        val privateKey: String,
        val keyPassword: String,
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
        val useSSLSecurity: Boolean,
        val truststorePath: String,
        val credstorePassword: String,
        val keystorePath: String,
    )

    data class PdlProperties(
        val pdlUrl: String,
        val pdlScope: String,
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
