package no.nav.sokos.skattekort.infrastructure

import io.getunleash.DefaultUnleash
import io.getunleash.FakeUnleash
import io.getunleash.Unleash
import io.getunleash.event.ClientFeaturesResponse
import io.getunleash.event.UnleashSubscriber
import io.getunleash.util.UnleashConfig

import no.nav.sokos.skattekort.config.PropertiesConfig

private const val TOGGLE_BESTILLINGER_SUFFIX = "bestillinger.enabled"
private const val TOGGLE_UTSENDINGER_SUFFIX = "utsendinger.enabled"
private const val TOGGLE_OPPDATERINGER_SUFFIX = "oppdateringer.enabled"
private const val TOGGLE_BEVISFORSENDING_SUFFIX = "bevisforsending.enabled"
private const val TOGGLE_FORESPOERSELINPUT_SUFFIX = "forespoerselinput.enabled"
private const val TOGGLE_LAGRE_MOTTATTE_BESTILLINGER_SUFFIX = "lagre-mottatte-bestillinger.enabled"
private const val TOGGLE_FORESPOERSEL_LISTENER_SUFFIX = "forespoersel-listener.enabled"

class UnleashIntegration(
    private val onForespoerselListenerChanged: (Boolean) -> Unit = {},
) {
    private val unleashClient: Unleash
    private val appProperties = PropertiesConfig.applicationProperties
    private val unleashProps = PropertiesConfig.unleashProperties

    private fun toggleName(suffix: String) = "${appProperties.appName}.$suffix"

    // Kill switcher:
    fun isUtsendingEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_UTSENDINGER_SUFFIX))

    fun isBestillingerEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_BESTILLINGER_SUFFIX))

    fun isOppdateringEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_OPPDATERINGER_SUFFIX))

    fun isBevisForSendingEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_BEVISFORSENDING_SUFFIX))

    fun isForespoerselInputEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_FORESPOERSELINPUT_SUFFIX))

    fun isLagreMottatteBestillingerEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_LAGRE_MOTTATTE_BESTILLINGER_SUFFIX))

    fun isForespoerselListenerEnabled(): Boolean = unleashClient.isEnabled(toggleName(TOGGLE_FORESPOERSEL_LISTENER_SUFFIX))

    init {
        if (appProperties.isTest) {
            unleashClient =
                FakeUnleash().also { fakeUnleash ->
                    fakeUnleash.enable(toggleName(TOGGLE_FORESPOERSEL_LISTENER_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_UTSENDINGER_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_BESTILLINGER_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_OPPDATERINGER_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_BEVISFORSENDING_SUFFIX))
                    fakeUnleash.enable(toggleName(TOGGLE_FORESPOERSELINPUT_SUFFIX))
                    fakeUnleash.disable(toggleName(TOGGLE_LAGRE_MOTTATTE_BESTILLINGER_SUFFIX))
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
