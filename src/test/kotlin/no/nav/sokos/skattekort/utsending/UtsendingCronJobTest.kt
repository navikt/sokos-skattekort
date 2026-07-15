package no.nav.sokos.skattekort.utsending

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.mockk

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.UnleashIntegration
import no.nav.sokos.skattekort.infrastructure.dare.UtsendingDareClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.person.Audit
import no.nav.sokos.skattekort.person.AuditService
import no.nav.sokos.skattekort.person.AuditTag
import no.nav.sokos.skattekort.person.PersonId
import no.nav.sokos.skattekort.utsending.mq.JmsProducerService

class UtsendingCronJobTest :
    FunSpec(
        {
            extensions(listOf(MQListener, DbListener))
            val utsendingDareClientService = mockk<UtsendingDareClientService>()
            val jmsProducerService: JmsProducerService by lazy {
                JmsProducerService(MQListener.connectionFactory)
            }
            val utsendingService =
                UtsendingService(
                    DbListener.dataSource,
                    jmsProducerService,
                    MQListener.utsendingsQueue,
                    MQListener.utsendingStorQueue,
                    UnleashIntegration(),
                    utsendingDareClientService,
                )
            val auditService = AuditService(DbListener.dataSource)

            test("Vi skal kunne sende ut et skattekort til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                utsendingService.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                auditEntries.map { it.tag } shouldContain (AuditTag.UTSENDING_OK)
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                messages.size shouldBe 1
                messages.first() shouldContain "03030312345"
                val utsendinger = utsendingService.getAllUtsendinger()
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
                utsendingService.handleUtsending()
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                messages.size shouldBe 0
                val utsendinger = utsendingService.getAllUtsendinger()
                withClue("Skal ha en utsending") { utsendinger.size shouldBe 1 }
                withClue("Skal ha failcount på en") { utsendinger[0].failCount shouldBe 3 }
            }
        },
    )
