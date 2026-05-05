package no.nav.sokos.skattekort.utsending

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk
import kotliquery.queryOf

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.person.Audit
import no.nav.sokos.skattekort.person.AuditService
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class UtsendingCronJobTest :
    FunSpec(
        {
            extensions(listOf(MQListener, DbListener))
            val utsendingDareClientService = mockk<UtsendingDareClientService>()
            val uut =
                UtsendingService(
                    DbListener.dataSource,
                    MQListener.connectionFactory,
                    MQListener.utsendingsQueue,
                    MQListener.utsendingStorQueue,
                    UnleashIntegration(),
                    utsendingDareClientService,
                )
            val auditService = AuditService(DbListener.dataSource)

            test("Vi skal kunne sende ut et skattekort til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                uut.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                auditEntries.map { it.tag } shouldContain (AuditTag.UTSENDING_OK)
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                messages.size shouldBe 1
                messages.first() shouldContain "03030312345"
                val utsendinger = uut.getAllUtsendinger()
                utsendinger.size shouldBe 0
            }

            test("Vi skal håndtere feil i utsendelse til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                DbListener.container.toDataSource().connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("UPDATE forskuddstrekk SET trekk_kode='foobar' WHERE id=5") // Vil ikke eksistere i Trekkode-enumen
                    }
                }
                uut.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                auditEntries.map { it.tag } shouldContain (AuditTag.UTSENDING_FEILET)
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                messages.size shouldBe 0
                val utsendinger = uut.getAllUtsendinger()
                withClue("Skal ha en utsending") { utsendinger.size shouldBe 1 }
                withClue("Skal ha failcount på en") { utsendinger[0].failCount shouldBe 1 }
            }

            test("utsending skal produsere eksakt copybook-format og lagre bevis i bevis_sending") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")

                uut.handleUtsending()

                val expectedCopybook =
                    "03030312345skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"

                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assertSoftly {
                    messages shouldHaveSize 1
                    messages.first() shouldBe expectedCopybook
                }

                DbListener.dataSource.transaction { tx ->
                    val sendinger =
                        tx.list(
                            queryOf("SELECT sending FROM bevis_sending"),
                        ) { row -> row.string("sending") }
                    assertSoftly {
                        sendinger shouldHaveSize 1
                        sendinger.first() shouldBe expectedCopybook
                    }
                }
            }

            test("utsending skal rute til stor-kø når forsystem er OS_STOR") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz_stor.sql")

                uut.handleUtsending()

                val messagesNormal = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                val messagesStor = JmsTestUtil.getMessages(MQListener.utsendingStorQueue)

                assertSoftly {
                    withClue("Normal kø skal være tom") { messagesNormal shouldHaveSize 0 }
                    withClue("Stor-kø skal ha én melding") { messagesStor shouldHaveSize 1 }
                    messagesStor.first() shouldContain "03030312345"
                }
            }
        },
    )
