package no.nav.sokos.lavendel.config

interface ConfigSource {
    fun get(key: String): String
}

object PropertiesConfig {
    data class Configuration(
        val applicationProperties: ApplicationProperties,
        val securityProperties: SecurityProperties,
        val postgresProperties: PostgresProperties,
    ) {
        constructor(source: ConfigSource) : this(
            applicationProperties = ApplicationProperties(source),
            securityProperties = SecurityProperties(source),
            postgresProperties = PostgresProperties(source),
        )
    }

    data class ApplicationProperties(
        val naisAppName: String,
        val profile: Profile,
    ) {
        constructor(source: ConfigSource) : this(
            naisAppName = source.get("APP_NAME"),
            profile = Profile.valueOf(source.get("APPLICATION_PROFILE")),
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
        val hostname: String = get("MQ_HOSTNAME"),
        val port: Int = get("MQ_PORT").toInt(),
        val mqQueueManagerName: String = get("MQ_QUEUE_MANAGER_NAME"),
        val mqChannelName: String = getOrEmpty("MQ_CHANNEL_NAME"),
        val userAuth: Boolean = true,
        val bestilleSkattekortQueueName: String = "bestille-skattekort",
    )

    enum class Profile {
        LOCAL,
        DEV,
        PROD,
    }
}
