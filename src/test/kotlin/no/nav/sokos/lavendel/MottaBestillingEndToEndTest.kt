package no.nav.sokos.lavendel

import java.time.LocalDateTime

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.withClue
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.extensions.time.withConstantNow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.lavendel.config.DatabaseConfig
import no.nav.sokos.lavendel.domain.Bestilling

class MottaBestillingEndToEndTest :
    EndToEndFunSpec({ dbContainer, jmsTestServer ->

        test("vi kan lagre en bestilling fra OS") {
            withConstantNow(LocalDateTime.parse("2025-04-12T00:00:00")) {
                jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
                TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)
                val dataSource: HikariDataSource = dbContainer.toDataSource()

                val fnr = "15467834260"

                jmsTestServer.sendMessage(jmsTestServer.bestillingsQueue, "OS;1994;$fnr")

                val rows: List<Bestilling> = TestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

                withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                    rows shouldHaveSize 1
                    rows.first().fnr shouldBe fnr
                }
                jmsTestServer.assertQueueIsEmpty(jmsTestServer.bestillingsQueue)
            }
        }
    })
