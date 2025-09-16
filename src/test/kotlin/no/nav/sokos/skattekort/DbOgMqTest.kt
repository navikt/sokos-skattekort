package no.nav.sokos.skattekort

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.DbTestUtil.readFromBestillings
import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.config.JmsListener
import no.nav.sokos.skattekort.forespoersel.ForespoerselListener
import no.nav.sokos.skattekort.forespoersel.ForespoerselService
import no.nav.sokos.skattekort.person.PersonRepository
import no.nav.sokos.skattekort.person.PersonService

class DbOgMqTest :
    FunSpec({
        extensions(listOf(JmsListener, DbListener))

        beforeSpec {
            ForespoerselListener(
                JmsListener.connectionFactory,
                ForespoerselService(DbListener.dataSource, PersonService(DbListener.dataSource, PersonRepository())),
                JmsListener.bestillingsQueue,
            )
        }

        test("Tester både kø og database") {

            JmsTestUtil.sendMessage("OS;1994;11111111111")
            eventually(1.seconds) {
                val result = readFromBestillings()
                result.size shouldBe 1
                result.first().inntektYear shouldBe "1994"
            }
        }

        afterTest { DbTestUtil.deleteAllTables(DbListener.dataSource) }
    })
