package no.nav.sokos.skattekort.utsending

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.BehaviorSpec
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
    BehaviorSpec(
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

            Given("et skattekort klart for utsending til oppdragz") {
                When("utsending håndteres") {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                    DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                    uut.handleUtsending()
                    val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                    val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                    val utsendinger = uut.getAllUtsendinger()

                    Then("skattekortet sendes til oppdragz og utsendingen fjernes") {
                        auditEntries.map { it.tag } shouldContain (AuditTag.UTSENDING_OK)
                        messages.size shouldBe 1
                        messages.first() shouldContain "03030312345"
                        utsendinger.size shouldBe 0
                    }
                }
            }

            Given("et skattekort klart for utsending til oppdragz med ugyldig trekkode") {
                When("utsending håndteres") {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                    DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                    DbListener.container.toDataSource().connection.use { connection ->
                        connection.createStatement().use { statement ->
                            statement.execute("UPDATE forskuddstrekk SET trekk_kode='foobar' WHERE id=5")
                        }
                    }
                    uut.handleUtsending()
                    val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                    val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                    val utsendinger = uut.getAllUtsendinger()

                    Then("feilen håndteres uten å sende melding og failcount økes") {
                        auditEntries.map { it.tag } shouldContain (AuditTag.UTSENDING_FEILET)
                        messages.size shouldBe 0
                        withClue("Skal ha en utsending") { utsendinger.size shouldBe 1 }
                        withClue("Skal ha failcount på en") { utsendinger[0].failCount shouldBe 1 }
                    }
                }
            }

            Given("et skattekort klart for utsending til oppdragz") {
                When("copybook-format produseres og bevis lagres") {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                    DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")

                    uut.handleUtsending()

                    val expectedCopybook =
                        "03030312345skattekortopplysningerOK                20252025-11-1119        kildeskattpensjonist                              1TrekkprosentpensjonFraNAV                                              018,50       12,0"

                    val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)

                    Then("meldingen får eksakt copybook-format og bevis lagres i bevis_sending") {
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
                }
            }

            Given("et skattekort for et forsystem som skal rutes til stor-kø") {
                When("utsending håndteres") {
                    DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                    DbListener.loadDataSet("database/utsending/skattekort_oppdragz_stor.sql")

                    uut.handleUtsending()

                    val messagesNormal = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                    val messagesStor = JmsTestUtil.getMessages(MQListener.utsendingStorQueue)

                    Then("normal kø er tom og stor-kø mottar meldingen") {
                        assertSoftly {
                            withClue("Normal kø skal være tom") { messagesNormal shouldHaveSize 0 }
                            withClue("Stor-kø skal ha én melding") { messagesStor shouldHaveSize 1 }
                            messagesStor.first() shouldContain "03030312345"
                        }
                    }
                }
            }
        },
    )
