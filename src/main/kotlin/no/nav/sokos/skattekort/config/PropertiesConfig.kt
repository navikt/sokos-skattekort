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

    data class AzureAdProperties(
        val clientId: String = getOrEmpty("AZURE_APP_CLIENT_ID"),
        val wellKnownUrl: String = getOrEmpty("AZURE_APP_WELL_KNOWN_URL"),
        val tenantId: String = getOrEmpty("AZURE_APP_TENANT_ID"),
        val clientSecret: String = getOrEmpty("AZURE_APP_CLIENT_SECRET"),
    )

    data class ApplicationProperties(
        val naisAppName: String = getOrEmpty("NAIS_APP_NAME"),
        val environment: Environment = Environment.valueOf(getOrEmpty("ENVIRONMENT")),
        val useAuthentication: Boolean = getOrEmpty("USE_AUTHENTICATION").toBoolean(),
    )

    data class PostgresProperties(
        val name: String = getOrEmpty("POSTGRES_NAME"),
        val host: String = getOrEmpty("POSTGRES_HOST"),
        val port: String = getOrEmpty("POSTGRES_PORT"),
        val username: String = getOrEmpty("POSTGRES_USER_USERNAME"),
        val password: String = getOrEmpty("POSTGRES_USER_PASSWORD"),
        val adminUsername: String = getOrEmpty("POSTGRES_ADMIN_USERNAME"),
        val adminPassword: String = getOrEmpty("POSTGRES_ADMIN_PASSWORD"),
        val adminRole: String = "$name-admin",
        val userRole: String = "$name-user",
        val vaultMountPath: String = getOrEmpty("VAULT_MOUNTPATH"),
    )

    data class MQProperties(
        val hostname: String = getOrEmpty("MQ_HOSTNAME"),
        val port: Int = getOrEmpty("MQ_PORT").toInt(),
        val mqQueueManagerName: String = getOrEmpty("MQ_QUEUE_MANAGER_NAME"),
        val mqChannelName: String = getOrEmpty("MQ_CHANNEL_NAME"),
        val serviceUsername: String = getOrEmpty("MQ_SERVICE_USERNAME"),
        val servicePassword: String = getOrEmpty("MQ_SERVICE_PASSWORD"),
        val userAuth: Boolean = true,
        val fraForSystemQueue: String = getOrEmpty("MQ_FRA_FORSYSTEM_ALT_QUEUE_NAME"),
    )

    enum class Environment {
        LOCAL,
        TEST,
        DEV,
        PROD,
    }

    fun isLocal() = ApplicationProperties().environment == Environment.LOCAL
}
