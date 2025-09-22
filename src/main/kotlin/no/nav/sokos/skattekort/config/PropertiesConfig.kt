package no.nav.sokos.skattekort.config

import java.io.File

import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.getAs

interface ConfigSource {
    fun get(key: String): String
}

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

    fun getApplicationProperties(): ApplicationProperties = envConfig.property("application").getAs<ApplicationProperties>()

    fun getPostgresProperties(): PostgresProperties? = envConfig.propertyOrNull("application.postgres")?.getAs<PostgresProperties>()

    fun getMQProperties(): MQProperties? = envConfig.propertyOrNull("application.mq")?.getAs<MQProperties>()

    fun getOrEmpty(key: String): String = envConfig.propertyOrNull(key)?.getString() ?: ""

    data class Configuration(
        val applicationProperties: ApplicationProperties,
        val securityProperties: SecurityProperties,
        val postgresProperties: PostgresProperties,
        val mqProperties: MQProperties,
    ) {
        constructor(source: ConfigSource) : this(
            applicationProperties = ApplicationProperties(source),
            securityProperties = SecurityProperties(source),
            postgresProperties = PostgresProperties(source),
            mqProperties = MQProperties(source),
        )
    }

    data class ApplicationProperties(
        val naisAppName: String,
        val profile: Profile,
        val useAuthentication: Boolean,
    ) {
        constructor(source: ConfigSource) : this(
            naisAppName = source.get("APP_NAME"),
            profile = Profile.valueOf(source.get("APPLICATION_PROFILE")),
            useAuthentication = source.get("USE_AUTHENTICATION").toBoolean(),
        )
    }

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
    ) {
        constructor(source: ConfigSource) : this(
            name = source.get("POSTGRES_NAME"),
            host = source.get("POSTGRES_HOST"),
            port = source.get("POSTGRES_PORT"),
            username = source.get("POSTGRES_USER_USERNAME").trim(),
            password = source.get("POSTGRES_USER_PASSWORD").trim(),
            adminUsername = source.get("POSTGRES_ADMIN_USERNAME").trim(),
            adminPassword = source.get("POSTGRES_ADMIN_PASSWORD").trim(),
            adminRole = "${source.get("POSTGRES_NAME")}-admin",
            userRole = "${source.get("POSTGRES_NAME")}-user",
            vaultMountPath = source.get("VAULT_MOUNTPATH"),
        )
    }

    data class SecurityProperties(
        val useAuthentication: Boolean,
        val azureAdProperties: AzureAdProperties,
    ) {
        constructor(source: ConfigSource) : this(
            useAuthentication = source.get("USE_AUTHENTICATION").toBoolean(),
            azureAdProperties = AzureAdProperties(source),
        )
    }

    data class AzureAdProperties(
        val clientId: String,
        val wellKnownUrl: String,
    ) {
        constructor(source: ConfigSource) : this(
            clientId = source.get("AZURE_APP_CLIENT_ID"),
            wellKnownUrl = source.get("AZURE_APP_WELL_KNOWN_URL"),
        )
    }

    data class MQProperties(
        val hostname: String,
        val port: Int,
        val mqQueueManagerName: String,
        val mqChannelName: String,
        val userAuth: Boolean,
        val bestilleSkattekortQueueName: String,
    ) {
        constructor(source: ConfigSource) : this(
            hostname = source.get("MQ_HOSTNAME").trim(),
            port = source.get("MQ_PORT").toInt(),
            mqChannelName = source.get("MQ_CHANNEL_NAME").trim(),
            mqQueueManagerName = source.get("MQ_QUEUE_MANAGER_NAME"),
            userAuth = source.get("MQ_USERAUTH").toBoolean(),
            bestilleSkattekortQueueName = source.get("MQ_BEST_QUEUE").trim(),
        )
    }

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }
}
