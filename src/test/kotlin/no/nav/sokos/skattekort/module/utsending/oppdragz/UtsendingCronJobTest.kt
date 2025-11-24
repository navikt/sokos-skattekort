package no.nav.sokos.skattekort.module.utsending.oppdragz

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FakeUnleashIntegration
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditService
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.mq.JmsProducerService
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class UtsendingCronJobTest :
    FunSpec(
        {
            extensions(listOf(MQListener, DbListener))

            val jmsProducerService = JmsProducerService(MQListener.connectionFactory)
            val utsendingService =
                UtsendingService(
                    DbListener.dataSource,
                    jmsProducerService,
                    MQListener.utsendingOppdragZQueue,
                    MQListener.utsendingDarePocQueue,
                    FakeUnleashIntegration(),
                )
            val auditService = AuditService(DbListener.dataSource)

            test("Vi skal kunne sende ut et skattekort til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                utsendingService.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                auditEntries.map { it.tag } shouldContain AuditTag.UTSENDING_OK

                val messages = JmsTestUtil.getMessages(MQListener.utsendingOppdragZQueue)
                messages.size shouldBe 1
                messages.first() shouldContain "12345678903"
                DbListener.dataSource.transaction { tx ->
                    val utsendinger = UtsendingRepository.getAllUtsendinger(tx)
                    utsendinger shouldBe emptyList()
                }
            }

            test("Vi skal håndtere feil i utsendelse til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                DbListener.container.toDataSource().connection.use { connection ->
                    connection.createStatement().use { statement ->
                        statement.execute("UPDATE forskuddstrekk SET trekk_kode='foobar' WHERE id=5") // Vil ikke eksistere i Trekkode-enumen
                    }
                }
                utsendingService.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                auditEntries.map { it.tag } shouldContain AuditTag.UTSENDING_FEILET

                JmsTestUtil.getMessages(MQListener.utsendingDarePocQueue) shouldBe emptyList()
                DbListener.dataSource.transaction { tx ->
                    val utsendinger = UtsendingRepository.getAllUtsendinger(tx)

                    withClue("Skal ha en utsending") { utsendinger.size shouldBe 1 }
                    withClue("Skal ha failcount på en") { utsendinger[0].failCount shouldBe 1 }
                }
            }
        },
    )
