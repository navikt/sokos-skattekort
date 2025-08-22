package no.nav.sokos.skattekort.alt

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestUtil
import no.nav.sokos.skattekort.TestUtil.readFromBestillings
import no.nav.sokos.skattekort.alt.config.DbListener
import no.nav.sokos.skattekort.alt.config.DbListener.dataSource
import no.nav.sokos.skattekort.alt.config.MQListener
import no.nav.sokos.skattekort.api.BestillingsListener
import no.nav.sokos.skattekort.api.Skattekortbestillingsservice

class DbOgMqTest :
    FunSpec({
        extensions(listOf(MQListener, DbListener))

        beforeSpec {
            BestillingsListener(
                MQListener.connectionFactory,
                Skattekortbestillingsservice(dataSource),
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

        afterTest { TestUtil.deleteAllTables(dataSource) }
    })
