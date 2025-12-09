package no.nav.sokos.skattekort.module.person

import java.time.LocalDate

import kotlinx.datetime.toJavaLocalDate

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class PersonServiceTest :
    FunSpec({
        extensions(DbListener)

        val personService by lazy {
            PersonService(DbListener.dataSource)
        }

        test("getPersonList skal returnere en liste med personer") {
            personService.getPersonList().isEmpty() shouldBe true

            DbListener.loadDataSet("database/person/persondata.sql")
            val personList = personService.getPersonList()
            personList.size shouldBe 10
            personList.forEach { person ->
                person.id shouldNotBe null
                person.flagget shouldBe false
                person.foedselsnummer.personId shouldBe person.id
                person.foedselsnummer.gjelderFom.toJavaLocalDate() shouldBe LocalDate.now()
                person.foedselsnummer.fnr.value.length shouldBe 11
            }
        }

        test("findOrCreatePersonByFnr skal returnere en person som er registrert") {
            val fnr = "10101000010"
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                val person =
                    personService.findOrCreatePersonByFnr(
                        fnr = Personidentifikator(fnr),
                        informasjon = "TEST",
                        brukerId = AUDIT_SYSTEM,
                        tx = tx,
                    )
                person shouldNotBe null
                person.foedselsnummer.fnr.value shouldBe fnr

                val personList = personService.getPersonList(tx = tx)
                personList.size shouldBe 10

                val auditList = AuditRepository.getAuditByPersonId(tx, person.id!!)
                auditList.size shouldBe 2
                auditList[1].tag shouldBe AuditTag.MOTTATT_FORESPOERSEL
                auditList[1].informasjon shouldBe "TEST"
            }
        }

        test("findOrCreatePersonByFnr skal returnere ny registrert person") {
            val fnr = "15467834260"
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                val person =
                    personService.findOrCreatePersonByFnr(
                        fnr = Personidentifikator(fnr),
                        informasjon = "TEST",
                        brukerId = AUDIT_SYSTEM,
                        tx = tx,
                    )
                person shouldNotBe null
                person.foedselsnummer.fnr.value shouldBe fnr

                val personList = personService.getPersonList(tx = tx)
                personList.size shouldBe 11

                val auditList = AuditRepository.getAuditByPersonId(tx, person.id!!)
                auditList.size shouldBe 1
                auditList.first().tag shouldBe AuditTag.OPPRETTET_PERSON
                auditList.first().informasjon shouldBe "TEST"
            }
        }
    })
