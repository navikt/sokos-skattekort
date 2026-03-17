package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.util.UnleashConfig
import mu.KotlinLogging

import no.nav.sokos.skattekort.config.PropertiesConfig

private val logger = KotlinLogging.logger { }

class UnleashIntegration {
    private val unleashClient: Unleash
    private val appProperties = PropertiesConfig.getApplicationProperties()
    private val unleashProps = PropertiesConfig.getUnleashProperties()

    // Kill switcher:
    fun isUtsendingEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.utsendinger.enabled")

    fun isBestillingerEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.bestillinger.enabled")

    fun isOppdateringEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.oppdateringer.enabled")

    fun isBevisForSendingEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.bevisforsending.enabled")

    fun isForespoerselInputEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.forespoerselinput.enabled")

    fun isLagreMottatteBestillingerEnabled(): Boolean = unleashClient.isEnabled("sokos-skattekort.lagre-mottatte-bestillinger.enabled")

    init {
        if (appProperties.environment == PropertiesConfig.Environment.TEST) {
            unleashClient =
                FakeUnleash().also { fakeUnleash ->
                    fakeUnleash.enable("sokos-skattekort.forespoerselinput.enabled")
                    fakeUnleash.enable("sokos-skattekort.utsendinger.enabled")
                    fakeUnleash.enable("sokos-skattekort.bestillinger.enabled")
                    fakeUnleash.enable("sokos-skattekort.oppdateringer.enabled")
                    fakeUnleash.enable("sokos-skattekort.bevisforsending.enabled")
                    fakeUnleash.disable("sokos-skattekort.lagre-mottatte-bestillinger.enabled")
                }
        } else {
            val config =
                UnleashConfig
                    .builder()
                    .appName(appProperties.naisAppName)
                    .instanceId(appProperties.podName)
                    .unleashAPI(unleashProps.unleashAPI + "/api/")
                    .apiKey(unleashProps.apiKey)
                    // TODO
//                    .environment(unleashProps.environment)
                    .synchronousFetchOnInitialisation(true)
                    .build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
