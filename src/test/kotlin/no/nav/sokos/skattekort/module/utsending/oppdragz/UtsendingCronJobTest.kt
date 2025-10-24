package no.nav.sokos.skattekort.module.utsending.oppdragz

import io.kotest.core.spec.style.FunSpec

import no.nav.sokos.skattekort.JmsTestUtil
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.MQListener
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditService
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
                println(auditEntries)
                assert(auditEntries.map { it.informasjon ?: "" }.any { it.contains("Skattekort sendt") })
                val messages = JmsTestUtil.getMessages(MQListener.utsendingsQueue)
                assert(messages.size == 1)
                assert(messages.first().contains("12345678903"))
            }
        },
    )
