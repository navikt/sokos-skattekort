package no.nav.sokos.skattekort.module.forespoersel

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.TestUtil.readFile
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.skattekort.Bestilling
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.module.utsending.Utsending
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
                utsendingList.size shouldBe 4

                verifyData(abonnementList, bestillingList, utsendingList, forespoersel)
            }
        }

        test("taImotForespoersel skal parse message fra OS og oppretter forespoersel, abonnement, bestilling og utsending") {
            val osMessage = "OS;2005;12345678901"

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
                utsendingList.size shouldBe 1

                verifyData(abonnementList, bestillingList, utsendingList, forespoersel)
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
                utsendingList.size shouldBe 4

                verifyData(abonnementList, bestillingList, utsendingList, forespoersel)
            }
        }

        test("taImotForespoersel skal parse message fra MANUELL og brukerId og oppretter forespoersel, abonnement, bestilling og utsending") {
            val message = "MANUELL;2005;12345678901"
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
                utsendingList.size shouldBe 1

                verifyData(abonnementList, bestillingList, utsendingList, forespoersel)

                val audit = AuditRepository.getAuditByPersonId(tx, abonnementList.first().person.id!!)
                audit.first().brukerId shouldBe brukerId
            }
        }
    })

private fun verifyData(
    abonnementList: List<Abonnement>,
    bestillingList: List<Bestilling>,
    utsendingList: List<Utsending>,
    forespoersel: Forespoersel,
) {
    val bestillingByPersonId = bestillingList.associateBy { it.personId.value }

    abonnementList.forEachIndexed { idx, abonnement ->
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

        val utsending = utsendingList[idx]
        utsending.abonnementId shouldBe abonnement.id
        utsending.fnr shouldBe abonnement.person.foedselsnummer.fnr
        utsending.inntektsaar shouldBe abonnement.inntektsaar
        utsending.forsystem shouldBe forespoersel.forsystem
    }
}
