package no.nav.sokos.skattekort

import java.time.LocalDateTime

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FakeUnleashIntegration
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.TestUtils.eventuallyConfiguration

class DbOgMqTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener))

        val forespoerselListener: ForespoerselListener by lazy {
            ForespoerselListener(
                MQListener.connectionFactory,
                ForespoerselService(
                    dataSource = DbListener.dataSource,
                    personService = PersonService(DbListener.dataSource),
                    featureToggles = FakeUnleashIntegration(),
                ),
                MQListener.bestillingsQueue,
            )
        }

        test("Tester både kø og database") {
            // Må ha withConstantNow pga. hvis denne testen kjører fra 15.12 til 31.12, så vil det bli 2 bestillinger
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                forespoerselListener.start()
                JmsTestUtil.sendMessage("OS;2025;11111111111")

                eventually(eventuallyConfiguration) {
                    DbListener.dataSource.transaction { session ->
                        val result = BestillingRepository.getBestillingsKandidaterForBatch(session)
                        result.size shouldBe 1
                        result.first().inntektsaar shouldBe 2025
                    }
                }
            }
        }
    })
