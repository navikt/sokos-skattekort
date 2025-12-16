package no.nav.sokos.skattekort.module.utsending.oppdragz

import kotlin.test.assertTrue

import io.kotest.core.spec.style.FunSpec
import junit.framework.TestCase.assertEquals
import kotliquery.queryOf

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.FakeUnleashIntegration
import no.nav.sokos.skattekort.infrastructure.MQListener
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditService
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.utsending.UtsendingService
import no.nav.sokos.skattekort.mq.JmsProducerService
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class UtsendingCronJobTest :
    FunSpec(
        {
            extensions(listOf(MQListener, DbListener))

            val jmsProducerService: JmsProducerService by lazy {
                JmsProducerService(MQListener.connectionFactory)
            }
            val utsendingService =
                UtsendingService(
                    DbListener.dataSource,
                    jmsProducerService,
                    MQListener.utsendingsQueue,
                    MQListener.utsendingStorQueue,
                    FakeUnleashIntegration(),
                )
            val auditService = AuditService(DbListener.dataSource)

            test("Vi skal kunne sende ut et skattekort til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                utsendingService.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                assertTrue(auditEntries.map { it.tag }.contains(AuditTag.UTSENDING_OK))
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assertEquals(messages.size, 1)
                assertTrue(messages.first().contains("12345678903"))
                val utsendinger = utsendingService.getAllUtsendinger()
                assertEquals(0, utsendinger.size)
            }

            test("Vi skal håndtere feil i utsendelse til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                DbListener.dataSource.transaction { session ->
                    session.execute(queryOf("UPDATE forskuddstrekk SET trekk_kode='foobar' WHERE id=5;"))
                }
                utsendingService.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                assertTrue(auditEntries.map { it.tag }.contains(AuditTag.UTSENDING_FEILET))
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assertTrue(messages.isEmpty())
                val utsendinger = utsendingService.getAllUtsendinger()
                assertEquals("Skal ha en utsending", 1, utsendinger.size)
                assertEquals("Skal ha failcount på en", 1, utsendinger[0].failCount)
            }
        },
    )
