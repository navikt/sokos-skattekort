package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig

import no.nav.sokos.skattekort.config.PropertiesConfig

class UnleashIntegration(
    private val unleashProps: PropertiesConfig.UnleashProperties,
    private val appProperties: PropertiesConfig.ApplicationProperties,
) {
    private lateinit var unleashClient: Unleash

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
                    .unleashAPI(unleashProps.unleashAPI)
                    .apiKey(unleashProps.apiKey)
                    .environment(unleashProps.environment)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
