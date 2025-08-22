package no.nav.sokos.skattekort

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestUtil.readFromBestillings
import no.nav.sokos.skattekort.api.BestillingsListener
import no.nav.sokos.skattekort.api.Skattekortbestillingsservice
import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.config.MQListener

class DbOgMqTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener))

        beforeSpec {
            BestillingsListener(
                MQListener.connectionFactory,
                Skattekortbestillingsservice(DbListener.dataSource),
                MQListener.bestillingMq,
            )
        }

        test("Tester både kø og database") {

            MQListener.sendMessage("OS;1994;11111111111")
            eventually(1.seconds) {
                val result = readFromBestillings()
                result.size shouldBe 1
                result.first().inntektYear shouldBe "1994"
            }
        }

        afterTest { TestUtil.deleteAllTables(DbListener.dataSource) }
    })
