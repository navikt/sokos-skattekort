package no.nav.sokos.skattekort

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestUtil.eventuallyConfiguration
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class DbOgMqTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener))

        val forespoerselListener: ForespoerselListener by lazy {
            ForespoerselListener(
                MQListener.connectionFactory,
                ForespoerselService(
                    dataSource = DbListener.dataSource,
                    personService = PersonService(DbListener.dataSource),
                ),
                MQListener.bestillingsQueue,
            )
        }

        test("Tester både kø og database") {
            forespoerselListener.start()
            JmsTestUtil.sendMessage("OS;2025;11111111111")

            eventually(eventuallyConfiguration) {
                DbListener.dataSource.transaction { session ->
                    val result = BestillingRepository.getAllBestilling(session)
                    result.size shouldBe 1
                    result.first().inntektsaar shouldBe 2025
                }
            }
        }
    })
