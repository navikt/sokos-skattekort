package no.nav.sokos.skattekort.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UnleashIntegrationTest :
    FunSpec({
        val unleash = UnleashIntegration()

        test("isUtsendingEnabled skal returnere true når UTSENDING flagget er på") {
            unleash.isUtsendingEnabled() shouldBe true
        }

        test("isBestillingerEnabled skal returnere true når BESTILLINGER flagget er på") {
            unleash.isBestillingerEnabled() shouldBe true
        }

        test("isOppdateringEnabled skal returnere true når OPPDATERING flagget er på") {
            unleash.isOppdateringEnabled() shouldBe true
        }

        test("isBevisForSendingEnabled skal returnere true når BEVIS_FOR_SENDING flagget er på") {
            unleash.isBevisForSendingEnabled() shouldBe true
        }

        test("isForespoerselInputEnabled skal returnere true når FORESPOERSEL_INPUT flagget er på") {
            unleash.isForespoerselInputEnabled() shouldBe true
        }

        test("isLagreMottatteBestillingerEnabled skal returnere false som default") {
            unleash.isLagreMottatteBestillingerEnabled() shouldBe false
        }

        test("isForespoerselListenerEnabled skal returnere true når FORESPOERSEL_LISTENER flagget er på") {
            unleash.isForespoerselListenerEnabled() shouldBe true
        }
    })
