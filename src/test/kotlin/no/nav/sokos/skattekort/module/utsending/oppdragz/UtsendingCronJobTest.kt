package no.nav.sokos.skattekort.module.utsending.oppdragz

import kotlin.test.assertTrue

import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.testcontainers.toDataSource
import junit.framework.TestCase.assertEquals

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditService
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonId
import no.nav.sokos.skattekort.module.utsending.UtsendingService

class UtsendingCronJobTest :
    FunSpec(
        {
            extensions(listOf(MQListener, DbListener))
            val uut =
                UtsendingService(
                    DbListener.dataSource,
                    MQListener.connectionFactory,
                    MQListener.utsendingsQueue,
                )
            val auditService = AuditService(DbListener.dataSource)

            test("Vi skal kunne sende ut et skattekort til oppdragz") {
                DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")
                DbListener.loadDataSet("database/utsending/skattekort_oppdragz.sql")
                uut.handleUtsending()
                val auditEntries: List<Audit> = auditService.getAuditByPersonId(PersonId(3))
                assertTrue(auditEntries.map { it.tag }.contains(AuditTag.UTSENDING_OK))
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assertTrue(messages.size == 1)
                assertTrue(messages.first().contains("12345678903"))
                val utsendinger = uut.getAllUtsendinger()
                assertEquals(0, utsendinger.size)
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
                assertTrue(auditEntries.map { it.tag }.contains(AuditTag.UTSENDING_FEILET))
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assertTrue(messages.size == 0)
                val utsendinger = uut.getAllUtsendinger()
                assertEquals("Skal ha en utsending", 1, utsendinger.size)
                assertEquals("Skal ha failcount på en", 1, utsendinger[0].failCount)
            }
        },
    )
