package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig

open class UnleashIntegration(
    unleashProps: PropertiesConfig.UnleashProperties,
    appProperties: PropertiesConfig.ApplicationProperties,
) {
    private var unleashClient: Unleash
    private val logger = KotlinLogging.logger {}

    // Kill switcher:
    fun isUtsendingEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.utsendinger.enabled", true)

    fun isBestillingerEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.bestillinger.enabled", true)

    fun isOppdateringEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.oppdateringer.enabled", true)

    fun isBevisForSendingEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.bevisforsending.enabled", true)

    fun isForespoerselInputEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.forespoerselinput.enabled", true)

    fun isLagreMottatteBestillingerEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.lagre-mottatte-bestillinger.enabled", false)

    init {
        if (appProperties.environment == PropertiesConfig.Environment.TEST ||
            appProperties.environment == PropertiesConfig.Environment.LOCAL
        ) {
            unleashClient = FakeUnleash().also { it.disable("sokos-skattekort.lagre-mottatte-bestillinger.enabled") }
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
