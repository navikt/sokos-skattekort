package no.nav.sokos.lavendel

import com.zaxxer.hikari.HikariDataSource
import io.kotest.assertions.withClue
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

import no.nav.sokos.lavendel.config.DatabaseConfig
import no.nav.sokos.lavendel.domain.Bestilling

class BasicEndToEndTest :
    ISpec({ dbContainer, jmsTestServer ->

        test("vi kan lagre en bestilling fra OS") {
            TestUtil.loadDataSet("basicendtoendtest/basicdata.sql", DatabaseConfig.dataSource)
            val dataSource: HikariDataSource = dbContainer.toDataSource()

            val fnr = "15467834260"

            jmsTestServer.sendMessage(jmsTestServer.bestillingsQueue, "OS;1994;$fnr")

            val rows: List<Bestilling> = TestUtil.storedBestillings(dataSource = dataSource, whereClause = "fnr = '$fnr'")

            withClue("Forventet at det er en bestilling i databasen med fnr $fnr") {
                rows shouldHaveSize 1
                rows.first().fnr shouldBe fnr
            }
        }
    })
