package no.nav.sokos.skattekort

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.domain.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.domain.person.PersonService
import no.nav.sokos.skattekort.domain.skattekort.BestillingRepository
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.JmsListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class DbOgMqTest :
    FunSpec({
        extensions(listOf(JmsListener, DbListener))

        val forespoerselListener: ForespoerselListener by lazy {
            ForespoerselListener(
                JmsListener.connectionFactory,
                ForespoerselService(
                    dataSource = DbListener.dataSource,
                    personService = PersonService(DbListener.dataSource),
                ),
                JmsListener.bestillingsQueue,
            )
        }

        test("Tester både kø og database") {
            forespoerselListener.start()
            JmsTestUtil.sendMessage("OS;1994;11111111111")
            delay(500.milliseconds)
            DbListener.dataSource.transaction { session ->
                val result = BestillingRepository.getAllBestilling(session)
                result.size shouldBe 1
                result.first().aar shouldBe 1994
            }
        }

        afterTest { DbTestUtil.deleteAllTables(DbListener.dataSource) }
    })
