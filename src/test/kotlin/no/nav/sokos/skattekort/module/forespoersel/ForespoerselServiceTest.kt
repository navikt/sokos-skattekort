package no.nav.sokos.skattekort.module.forespoersel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.utsending.UtsendingRepository
import no.nav.sokos.skattekort.security.NavIdent
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class ForespoerselServiceTest :
    FunSpec({
        extensions(DbListener)

        val personService: PersonService by lazy {
            PersonService(DbListener.dataSource)
        }

        val forespoerselService: ForespoerselService by lazy {
            ForespoerselService(DbListener.dataSource, personService)
        }

        test("taImotForespoersel skal parse message fra Arena og oppretter forespoersel, abonnement, bestilling og utsending") {
            val arenaXml = readFile("/mq/eskattekortbestilling_arena.xml")

            forespoerselService.taImotForespoersel(arenaXml)

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 1
                val forespoersel = forespoerselList.first()
                forespoersel.forsystem shouldBe Forsystem.ARENA

                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 4
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 4
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                verifyData(abonnementList, bestillingList, forespoersel)
            }
        }

        test("taImotForespoersel skal parse message fra OS og oppretter forespoersel, abonnement, bestilling og utsending") {
            val osMessage = "OS;2025;12345678901"

            forespoerselService.taImotForespoersel(osMessage)

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 1
                val forespoersel = forespoerselList.first()
                forespoersel.forsystem shouldBe Forsystem.OPPDRAGSSYSTEMET

                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 1
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                verifyData(abonnementList, bestillingList, forespoersel)
            }
        }

        test("taImotForespoersel skal parse message fra Arena og oppretter bestilling der skattekort ikke fantes fra før") {
            DbListener.loadDataSet("database/skattekort/person_med_skattekort.sql")

            val arenaXml = readFile("/mq/eskattekortbestilling_arena.xml")
            forespoerselService.taImotForespoersel(arenaXml)

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 1
                val forespoersel = forespoerselList.first()
                forespoersel.forsystem shouldBe Forsystem.ARENA

                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 4
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                verifyData(abonnementList, bestillingList, forespoersel)
            }
        }

        test("taImotForespoersel skal parse message fra MANUELL og brukerId og oppretter forespoersel, abonnement, bestilling og utsending") {
            val message = "MANUELL;2026;12345678901"
            val brukerId = "Z123456"

            forespoerselService.taImotForespoersel(message, NavIdent(brukerId))

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 1
                val forespoersel = forespoerselList.first()
                forespoersel.forsystem shouldBe Forsystem.MANUELL

                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 1
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                verifyData(abonnementList, bestillingList, forespoersel)

                val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                auditList.first().brukerId shouldBe brukerId
            }
        }

        test("taImotForespoersel med samme person og årstall som en tidligere forespoersel, skal det opprette kun en bestilling") {
            val message1 = "OS;2025;12345678901"
            val message2 = "MANUELL;2025;12345678901"

            forespoerselService.taImotForespoersel(message1)
            forespoerselService.taImotForespoersel(message2)

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 2
                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 2
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                auditList[0].tag shouldBe AuditTag.MOTTATT_FORESPOERSEL
                auditList[1].tag shouldBe AuditTag.OPPRETTET_PERSON
            }
        }

        test("taImotForespoersel med samme forsystem, person og årstall som en tidligere forespoersel, skal det kun audit logges dersom en utsending ikke er utført") {
            val message = "OS;2025;12345678901"

            forespoerselService.taImotForespoersel(message)
            forespoerselService.taImotForespoersel(message)

            DbListener.dataSource.transaction { tx ->
                val forespoerselList = ForespoerselRepository.getAllForespoersel(tx)
                forespoerselList.size shouldBe 2
                val abonnementList = AbonnementRepository.getAllAbonnementer(tx)
                abonnementList.size shouldBe 2
                val bestillingList = BestillingRepository.getAllBestilling(tx)
                bestillingList.size shouldBe 1
                val utsendingList = UtsendingRepository.getAllUtsendinger(tx)
                utsendingList.size shouldBe 0

                val auditList = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                auditList[0].tag shouldBe AuditTag.MOTTATT_FORESPOERSEL
                auditList[1].tag shouldBe AuditTag.OPPRETTET_PERSON
            }
        }
    })

private fun verifyData(
    abonnementList: List<Abonnement>,
    bestillingList: List<Bestilling>,
    forespoersel: Forespoersel,
) {
    val bestillingByPersonId = bestillingList.associateBy { it.personId.value }

    abonnementList.forEach { abonnement ->
        abonnement.forespoersel.id shouldBe forespoersel.id

        val bestilling =
            abonnement.person.id
                ?.value
                ?.let { bestillingByPersonId[it] }
        if (bestilling != null) {
            abonnement.person.foedselsnummer.fnr shouldBe bestilling.fnr
            abonnement.inntektsaar shouldBe bestilling.inntektsaar
            bestilling.bestillingsbatchId shouldBe null
        }
    }
}
