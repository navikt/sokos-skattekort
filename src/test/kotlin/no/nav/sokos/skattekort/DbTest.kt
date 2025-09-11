package no.nav.sokos.skattekort

import kotlin.time.Duration.Companion.seconds

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.skattekort.config.DbListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class DbTest :
    FunSpec({
        extensions(listOf(DbListener))

        test("db test") {
            DbListener.dataSource.transaction { session ->
                session.update(
                    queryOf(
                        "INSERT INTO BESTILLING (FNR, INNTEKTSAAR) VALUES (:fnr,:inntektsaar)",
                        mapOf(
                            "fnr" to "22222222222",
                            "inntektsaar" to "1997",
                        ),
                    ),
                )
            }

            eventually(1.seconds) {
                val result =
                    DbListener.dataSource.transaction { session ->
                        session.run(queryOf("SELECT fnr FROM bestilling").map { it.string(1) }.asSingle)
                    }
                result shouldBe "22222222222"
            }
        }

        afterTest { DbTestUtil.deleteAllTables(DbListener.dataSource) }
    })
