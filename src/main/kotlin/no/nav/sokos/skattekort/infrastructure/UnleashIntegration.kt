package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig

open class UnleashIntegration(
    private val unleashProps: PropertiesConfig.UnleashProperties,
    private val appProperties: PropertiesConfig.ApplicationProperties,
) {
    private lateinit var unleashClient: Unleash
    private val logger = KotlinLogging.logger {}

    fun isUtsendingEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.utsendinger.enabled", false)

    fun isBestillingerEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.bestillinger.enabled", false)

    init {
        if (appProperties.environment == PropertiesConfig.Environment.TEST ||
            appProperties.environment == PropertiesConfig.Environment.LOCAL
        ) {
            unleashClient = FakeUnleash()
        } else {
            val config =
                UnleashConfig
                    .builder()
                    .appName(appProperties.naisAppName)
                    .instanceId(appProperties.podName)
                    .unleashAPI(unleashProps.unleashAPI + "/api/")
                    .apiKey(unleashProps.apiKey)
                    .environment(unleashProps.environment)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
