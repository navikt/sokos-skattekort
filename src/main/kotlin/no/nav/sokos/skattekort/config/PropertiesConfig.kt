package no.nav.sokos.skattekort.config

import java.io.File

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig

object PropertiesConfig {
    private var envConfig: HoconApplicationConfig = HoconApplicationConfig(ConfigFactory.empty())

    fun initEnvConfig(applicationConfig: ApplicationConfig? = null) {
        val environment = System.getenv("APPLICATION_ENV") ?: System.getProperty("APPLICATION_ENV")
        val config =
            when {
                environment == null || environment.lowercase() == "local" -> {
                    val defaultConfig = ConfigFactory.parseFile(File("defaults.properties"))
                    ConfigFactory.parseResources("application-local.conf").withFallback(defaultConfig)
                }

                else -> ConfigFactory.parseResources("application-${environment.lowercase()}.conf")
            }

        envConfig = applicationConfig?.let { external ->
            HoconApplicationConfig(config.withFallback(ConfigFactory.parseMap(external.toMap())).resolve())
        } ?: HoconApplicationConfig(config.resolve())
    }

    fun getOrEmpty(key: String): String = envConfig.propertyOrNull(key)?.getString() ?: ""

    fun getApplicationProperties(): ApplicationProperties =
        ApplicationProperties(
            naisAppName = getOrEmpty("NAIS_APP_NAME"),
            environment = Environment.valueOf(getOrEmpty("ENVIRONMENT")),
            useAuthentication = getOrEmpty("USE_AUTHENTICATION").toBoolean(),
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
            adminRole = "${getOrEmpty("POSTGRES_NAME")}-admin",
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
            fraForSystemQueue = getOrEmpty("MQ_FRA_FORSYSTEM_ALT_QUEUE_NAME"),
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
