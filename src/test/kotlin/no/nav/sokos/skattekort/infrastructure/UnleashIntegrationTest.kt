package no.nav.sokos.skattekort.infrastructure

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class UnleashIntegrationTest :
    FunSpec({
        val unleash = UnleashIntegration()

        test("isUtsendingEnabled skal returnere true som default") {
            unleash.isUtsendingEnabled() shouldBe true
        }

        test("isBestillingerEnabled skal returnere true som default") {
            unleash.isBestillingerEnabled() shouldBe true
        }

        test("isOppdateringEnabled skal returnere true som default") {
            unleash.isOppdateringEnabled() shouldBe true
        }

        test("isBevisForSendingEnabled skal returnere true som default") {
            unleash.isBevisForSendingEnabled() shouldBe true
        }

        test("isForespoerselInputEnabled skal returnere true som default") {
            unleash.isForespoerselInputEnabled() shouldBe true
        }

        test("isLagreMottatteBestillingerEnabled skal returnere false som default") {
            unleash.isLagreMottatteBestillingerEnabled() shouldBe false
        }

        test("isForespoerselListenerEnabled skal returnere true som default") {
            unleash.isForespoerselListenerEnabled() shouldBe true
        }
    })
