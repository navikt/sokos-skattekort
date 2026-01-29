package no.nav.sokos.skattekort.kafka

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.DbListener
import no.nav.sokos.skattekort.infrastructure.WiremockListener
import no.nav.sokos.skattekort.infrastructure.WiremockListener.wiremockPDLStub
import no.nav.sokos.skattekort.module.person.Audit
import no.nav.sokos.skattekort.module.person.AuditRepository
import no.nav.sokos.skattekort.module.person.AuditTag
import no.nav.sokos.skattekort.module.person.Person
import no.nav.sokos.skattekort.module.person.PersonRepository
import no.nav.sokos.skattekort.module.person.PersonService
import no.nav.sokos.skattekort.module.person.Personidentifikator
import no.nav.sokos.skattekort.module.skattekort.BestillingRepository
import no.nav.sokos.skattekort.pdl.PdlClientService
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.TestUtils.readFile

class IdentifikatorEndringServiceTest :
    FunSpec({
        extensions(DbListener, WiremockListener)

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                client = createHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        val identifikatorEndringService: IdentifikatorEndringService by lazy {
            IdentifikatorEndringService(
                dataSource = DbListener.dataSource,
                pdlClientService = pdlClientService,
                personService = PersonService(DbListener.dataSource, pdlClientService),
            )
        }

        test("processIdentifikatorEndring oppdater person med ny folkeregisteridentifikator") {
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                PersonRepository.getAllPersonById(tx, 20, null).size shouldBe 10
            }

            val pdlResponse = readFile("/pdl/hentIdenterBolkOkResponse.json")
            wiremockPDLStub(pdlResponse)

            val hendelse = getPersonHendelseMockData()
            val personidentifikator = Personidentifikator(hendelse.folkeregisteridentifikator!!.identifikasjonsnummer)

            identifikatorEndringService.processIdentifikatorEndring(hendelse)
            DbListener.dataSource.transaction { tx ->
                val person = PersonRepository.findPersonByFnr(tx, personidentifikator)!!
                person.foedselsnummer.fnr shouldBe personidentifikator

                val auditList = AuditRepository.getAuditByPersonId(tx, person.id!!)
                withClue("Skal ha 3 audit meldinger") { auditList.size shouldBe 3 }
                auditMatcher(auditList[1], person)

                val bestillingList = BestillingRepository.getBestillingsKandidaterForBatch(tx)
                withClue("Skal opprette 1 ny bestilling") { bestillingList.size shouldBe 1 }
            }
        }

        test("processIdentifikatorEndring oppdater person med ny folkeregisteridentifikator (KORRIGERT)") {
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                PersonRepository.getAllPersonById(tx, 20, null).size shouldBe 10
            }

            val pdlResponse = readFile("/pdl/hentIdenterBolkOkResponse.json")
            wiremockPDLStub(pdlResponse)

            val hendelse =
                getPersonHendelseMockData().copy(
                    endringstype = EndringstypeDTO.KORRIGERT,
                )
            val personidentifikator = Personidentifikator(hendelse.folkeregisteridentifikator!!.identifikasjonsnummer)

            identifikatorEndringService.processIdentifikatorEndring(hendelse)
            DbListener.dataSource.transaction { tx ->
                val person = PersonRepository.findPersonByFnr(tx, personidentifikator)!!
                person.foedselsnummer.fnr shouldBe personidentifikator

                val auditList = AuditRepository.getAuditByPersonId(tx, person.id!!)
                withClue("Skal ha 3 audit meldinger") { auditList.size shouldBe 3 }
                auditMatcher(auditList[1], person)

                val bestillingList = BestillingRepository.getBestillingsKandidaterForBatch(tx)
                withClue("Skal opprette 1 ny bestilling") { bestillingList.size shouldBe 1 }
            }
        }

        test("processIdentifikatorEndring ignorer med andre opplysningstype") {
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                PersonRepository.getAllPersonById(tx, 20, null).size shouldBe 10
            }

            val hendelse =
                getPersonHendelseMockData().copy(
                    endringstype = EndringstypeDTO.OPPHOERT,
                )
            val personidentifikator = Personidentifikator(hendelse.folkeregisteridentifikator!!.identifikasjonsnummer)

            identifikatorEndringService.processIdentifikatorEndring(hendelse)
            DbListener.dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, personidentifikator) shouldBe null
            }
        }

        test("processIdentifikatorEndring ignorer med ingen historiske identer funnet fra PDL") {
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                PersonRepository.getAllPersonById(tx, 20, null).size shouldBe 10
            }

            val pdlResponse = readFile("/pdl/hentIdenterBolkOkUtenHistoriskResponse.json")
            wiremockPDLStub(pdlResponse)

            val hendelse = getPersonHendelseMockData()
            val personidentifikator = Personidentifikator(hendelse.folkeregisteridentifikator!!.identifikasjonsnummer)

            identifikatorEndringService.processIdentifikatorEndring(hendelse)
            DbListener.dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, personidentifikator) shouldBe null
            }
        }

        test("processIdentifikatorEndring ignorer med folkeregisteridentifikator ikke er FOLKEREGISTERIDENTIFIKATOR_V1") {
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                PersonRepository.getAllPersonById(tx, 20, null).size shouldBe 10
            }

            val hendelse =
                getPersonHendelseMockData().copy(
                    opplysningstype = "ANNEN_IDENTIFIKATOR",
                )
            val personidentifikator = Personidentifikator(hendelse.folkeregisteridentifikator!!.identifikasjonsnummer)

            identifikatorEndringService.processIdentifikatorEndring(hendelse)
            DbListener.dataSource.transaction { tx ->
                PersonRepository.findPersonByFnr(tx, personidentifikator) shouldBe null
            }
        }
    })

private fun auditMatcher(
    audit: Audit,
    person: Person,
) {
    audit.personId.value shouldBe person.id?.value
    audit.brukerId shouldBe "system"
    audit.tag shouldBe AuditTag.OPPDATERT_PERSONIDENTIFIKATOR
    audit.informasjon shouldBe "Oppdatert foedselsnummer: ${person.foedselsnummer.fnr.value}"
}

private fun getPersonHendelseMockData() =
    PersonHendelseDTO(
        hendelseId = "aba9b36f-43cd-4b5b-b4e8-f991af902bbe",
        personidenter = listOf("24519539620", "2294850419623"),
        opplysningstype = FOLKEREGISTERIDENTIFIKATOR,
        endringstype = EndringstypeDTO.OPPRETTET,
        folkeregisteridentifikator =
            FolkeregisteridentifikatorDTO(
                identifikasjonsnummer = "24519539620",
                type = "FNR",
                status = "iBruk",
            ),
    )
