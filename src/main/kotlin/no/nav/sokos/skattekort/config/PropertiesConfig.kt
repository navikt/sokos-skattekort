package no.nav.sokos.skattekort.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

import com.nimbusds.jose.jwk.RSAKey
import com.typesafe.config.ConfigFactory
import io.ktor.server.config.ApplicationConfig
import io.ktor.server.config.HoconApplicationConfig
import io.ktor.server.config.getAs
import io.ktor.server.config.withFallback

object PropertiesConfig {
    lateinit var config: ApplicationConfig
        private set

    fun load(applicationConfig: ApplicationConfig) {
        if (!::config.isInitialized) {
            config = applicationConfig
        }
    }

    val applicationProperties by lazy {
        config.property("application").getAs<ApplicationProperties>()
    }

    val azureAdProperties by lazy {
        config.property("azureAd").getAs<AzureAdProperties>()
    }

    val postgresProperties by lazy {
        config.property("postgres").getAs<PostgresProperties>()
    }

    val mqProperties by lazy {
        config.property("mq").getAs<MQProperties>()
    }

    val schedulerProperties by lazy {
        config.property("scheduler").getAs<SchedulerProperties>()
    }

    val maskinportenProperties by lazy {
        config.property("maskinporten").getAs<MaskinportenProperties>()
    }

    val skatteetatenProperties by lazy {
        config.property("skatteetaten").getAs<SkatteetatenProperties>()
    }

    val darePocProperties by lazy {
        config.property("darePoc").getAs<DarePocProperties>()
    }

    val kafkaProperties by lazy {
        config.property("kafka").getAs<KafkaProperties>()
    }

    val pdlProperties by lazy {
        config.property("pdl").getAs<PdlProperties>()
    }

    val tilgangsmaskinProperties by lazy {
        config.property("tilgangsmaskin").getAs<TilgangsmaskinProperties>()
    }

    val unleashProperties by lazy {
        config.property("unleash").getAs<UnleashProperties>()
    }

    val isLocal: Boolean
        get() = applicationProperties.isLocal

    val isTest: Boolean
        get() = applicationProperties.isTest

    val isProd: Boolean
        get() = applicationProperties.isProd

    @Serializable
    data class AzureAdProperties(
        val clientId: String,
        val wellKnownUrl: String,
        val tenantId: String,
        val clientSecret: String,
        val providerName: String,
    )

    @Serializable
    data class ApplicationProperties(
        val profile: Profile,
        val appName: String,
        val podName: String,
        val gyldigeFnr: String,
        val bestillingOrgnr: String,
        val mqListenerEnabled: Boolean,
    ) {
        val isLocal = profile == Profile.LOCAL
        val isTest = profile == Profile.TEST
        val isProd = profile == Profile.PROD
    }

    @Serializable
    data class PostgresProperties(
        val jdbcUrl: String,
        val name: String,
        val host: String,
        val port: String,
        val username: String,
        val password: String,
    )

    @Serializable
    data class MQProperties(
        val hostname: String,
        val port: Int,
        val queueManagerName: String,
        val channelName: String,
        val serviceUsername: String,
        val servicePassword: String,
        val userAuth: Boolean,
        val fraForSystemQueue: String,
        val leveransekoeOppdragZSkattekort: String,
        val leveransekoeOppdragZSkattekortStor: String,
    )

    @Serializable
    data class SchedulerProperties(
        val enabled: Boolean,
        val cronBestilling: String,
        val cronUtsending: String,
        val cronHentSkattekort: String,
        val cronHentOppdaterte: String,
        val cronFetchMetrics: String,
        val cronForespoerselInput: String,
        val cronDeleteSkattekort: String,
    )

    @Serializable
    data class MaskinportenProperties(
        val clientId: String,
        val wellKnownUrl: String,
        val rsaKeyString: String,
        val scopes: String,
        val systemBrukerClaim: String,
    ) {
        val rsaKey: RSAKey? by lazy {
            rsaKeyString.takeIf { it.isNotBlank() }?.let { RSAKey.parse(it) }
        }
    }

    @Serializable
    data class SkatteetatenProperties(
        val skatteetatenUrl: String,
    )

    @Serializable
    data class DarePocProperties(
        val darePocUrl: String,
        val darePocScope: String,
    )

    @Serializable
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

    @Serializable
    data class PdlProperties(
        val pdlUrl: String,
        val pdlScope: String,
    )

    @Serializable
    data class TilgangsmaskinProperties(
        val tilgangsmaskinUrl: String,
        val tilgangsmaskinScope: String,
    )

    @Serializable
    data class UnleashProperties(
        @SerialName("unleashApi") val unleashAPI: String,
        val apiKey: String,
        val environment: String,
    )
}

fun ApplicationConfig.loadEnvironmentConfig(): ApplicationConfig {
    val hoconConfig = HoconApplicationConfig(ConfigFactory.load())
    val environmentName =
        System.getenv("APPLICATION_ENV")
            ?: System.getenv("NAIS_CLUSTER_NAME")
            ?: System.getProperty("NAIS_CLUSTER_NAME")
    val environment = environmentName?.lowercase()?.substringBefore("-") ?: "local"

    val environmentConfig = ApplicationConfig("application-$environment.conf")
    return this overriding environmentConfig overriding hoconConfig

infix fun ApplicationConfig.overriding(other: ApplicationConfig): ApplicationConfig = this.withFallback(other)

enum class Profile {
    LOCAL,
    DEV,
    Q1,
    TEST,
    PROD,
}
