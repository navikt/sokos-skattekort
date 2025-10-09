package no.nav.sokos.skattekort

import java.time.LocalDate

import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime

import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotliquery.queryOf

import no.nav.sokos.skattekort.domain.person.PersonId
import no.nav.sokos.skattekort.domain.person.PersonRepository
import no.nav.sokos.skattekort.domain.person.Personidentifikator
import no.nav.sokos.skattekort.domain.skattekort.Bestilling
import no.nav.sokos.skattekort.domain.skattekort.BestillingRepository
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction

@OptIn(ExperimentalTime::class)
class DbTest :
    FunSpec({
        extensions(listOf(DbListener))

        test("db test") {
            DbListener.dataSource.transaction { session ->
                val personId =
                    PersonRepository.insert(
                        tx = session,
                        fnr = Personidentifikator("22222222222"),
                        gjelderFom = LocalDate.now(),
                        informasjon = "",
                    )
                BestillingRepository.insert(
                    session,
                    Bestilling(
                        personId = PersonId(personId!!),
                        fnr = Personidentifikator("22222222222"),
                        inntektsaar = 2023,
                    ),
                )
            }

            eventually(1.seconds) {
                val result =
                    DbListener.dataSource.transaction { session ->
                        session.run(queryOf("SELECT fnr FROM bestillinger").map { it.string(1) }.asSingle)
                    }
                result shouldBe "22222222222"
            }
        }

        afterTest { DbTestUtil.deleteAllTables(DbListener.dataSource) }
    })
