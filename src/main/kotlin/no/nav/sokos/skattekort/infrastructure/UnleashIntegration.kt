package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.event.ClientFeaturesResponse
import io.getunleash.event.UnleashSubscriber
import io.getunleash.util.UnleashConfig

import no.nav.sokos.skattekort.config.PropertiesConfig

private const val SOKOS_SKATTEKORT_OPPDATERINGER_ENABLED = "sokos-skattekort.oppdateringer.enabled"
private const val SOKOS_SKATTEKORT_BEVISFORSENDING_ENABLED = "sokos-skattekort.bevisforsending.enabled"
private const val SOKOS_SKATTEKORT_FORESPOERSELINPUT_ENABLED = "sokos-skattekort.forespoerselinput.enabled"
private const val SOKOS_SKATTEKORT_LAGRE_MOTTATTE_BESTILLINGER_ENABLED = "sokos-skattekort.lagre-mottatte-bestillinger.enabled"
private const val SOKOS_SKATTEKORT_FORESPOERSEL_LISTENER_ENABLED = "sokos-skattekort.forespoersel-listener.enabled"
private const val SOKOS_SKATTEKORT_UTSENDINGER_ENABLED = "sokos-skattekort.utsendinger.enabled"
private const val SOKOS_SKATTEKORT_BESTILLINGER_ENABLED = "sokos-skattekort.bestillinger.enabled"

class UnleashIntegration(
    private val onForespoerselListenerChanged: (Boolean) -> Unit = {},
) {
    private val unleashClient: Unleash
    private val appProperties = PropertiesConfig.applicationProperties
    private val unleashProps = PropertiesConfig.unleashProperties

    // Kill switcher:
    fun isUtsendingEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_UTSENDINGER_ENABLED)

    fun isBestillingerEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_BESTILLINGER_ENABLED)

    fun isOppdateringEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_OPPDATERINGER_ENABLED)

    fun isBevisForSendingEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_BEVISFORSENDING_ENABLED)

    fun isForespoerselInputEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_FORESPOERSELINPUT_ENABLED)

    fun isLagreMottatteBestillingerEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_LAGRE_MOTTATTE_BESTILLINGER_ENABLED)

    fun isForespoerselListenerEnabled(): Boolean = unleashClient.isEnabled(SOKOS_SKATTEKORT_FORESPOERSEL_LISTENER_ENABLED)

    init {
        if (appProperties.isTest) {
            unleashClient =
                FakeUnleash().also { fakeUnleash ->
                    fakeUnleash.enable(SOKOS_SKATTEKORT_FORESPOERSEL_LISTENER_ENABLED)
                    fakeUnleash.enable(SOKOS_SKATTEKORT_UTSENDINGER_ENABLED)
                    fakeUnleash.enable(SOKOS_SKATTEKORT_BESTILLINGER_ENABLED)
                    fakeUnleash.enable(SOKOS_SKATTEKORT_OPPDATERINGER_ENABLED)
                    fakeUnleash.enable(SOKOS_SKATTEKORT_BEVISFORSENDING_ENABLED)
                    fakeUnleash.enable(SOKOS_SKATTEKORT_FORESPOERSELINPUT_ENABLED)
                    fakeUnleash.disable(SOKOS_SKATTEKORT_LAGRE_MOTTATTE_BESTILLINGER_ENABLED)
                }
        } else {
            val config =
                UnleashConfig
                    .builder()
                    .appName(appProperties.appName)
                    .instanceId(appProperties.podName)
                    .unleashAPI(unleashProps.unleashAPI + "/api/")
                    .apiKey(unleashProps.apiKey)
                    .synchronousFetchOnInitialisation(true)
                    .subscriber(
                        object : UnleashSubscriber {
                            override fun togglesFetched(toggleResponse: ClientFeaturesResponse) {
                                onForespoerselListenerChanged(isForespoerselListenerEnabled())
                            }
                        },
                    ).build()
            unleashClient = DefaultUnleash(config)
        }
    }
}
