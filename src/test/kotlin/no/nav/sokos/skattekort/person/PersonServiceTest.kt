package no.nav.sokos.skattekort.person

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContainNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import no.nav.pdl.HentIdenterBolk
import no.nav.pdl.enums.IdentGruppe
import no.nav.pdl.hentidenterbolk.HentIdenterBolkResult
import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.sokos.skattekort.infrastructure.pdl.GraphQLResponse
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.util.SQLUtils.transaction
import no.nav.sokos.skattekort.utils.MockResponse
import no.nav.sokos.skattekort.utils.generateHentIdenterBolk
import no.nav.sokos.skattekort.utils.mockPdlClientService

class PersonServiceTest :
    FunSpec({
        extensions(DbListener)

        fun createPersonService(vararg responses: MockResponse): PersonService {
            val (_, pdlClientService) = mockPdlClientService(*responses)
            return PersonService(DbListener.dataSource, pdlClientService)
        }

        test("Skal ikke kaste exception når det kommer inn en tom liste") {
            val personService = createPersonService()
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(emptyList(), AUDIT_SYSTEM)
            result.size shouldBe 0
        }

        test("findOrCreatePersonByFnr skal returnere en person som er registrert") {
            val fnr = "10101000010"
            val personService = createPersonService()
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                val (personId, opprettet) =
                    personService
                        .findPersonIdOrCreatePersonByFnr(
                            fnr = Personidentifikator(fnr),
                            informasjon = "TEST",
                            brukerId = AUDIT_SYSTEM,
                            tx = tx,
                        )
                personId shouldNotBe null
                opprettet shouldBe false

                val auditList = AuditRepository.getAuditByPersonId(tx, personId)
                auditList.size shouldBe 1
                auditList.first().tag shouldBe AuditTag.OPPRETTET_PERSON
                auditList.first().informasjon shouldBe "Person 10 opprettet"
            }
        }

        test("findOrCreatePersonByFnr skal returnere ny registrert person") {
            val fnr = "15467834260"
            val personService = createPersonService()
            DbListener.loadDataSet("database/person/persondata.sql")
            DbListener.dataSource.transaction { tx ->
                val (personId, opprettet) =
                    personService
                        .findPersonIdOrCreatePersonByFnr(
                            fnr = Personidentifikator(fnr),
                            informasjon = "TEST",
                            brukerId = AUDIT_SYSTEM,
                            tx = tx,
                        )
                personId shouldNotBe null
                opprettet shouldBe true

                val auditList = AuditRepository.getAuditByPersonId(tx, personId)
                auditList.size shouldBe 1
                auditList.first().tag shouldBe AuditTag.OPPRETTET_PERSON
                auditList.first().informasjon shouldBe "TEST"
            }
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal returnere map med personId for eksisterende personer") {
            val fnrList = listOf("10101000010", "10101000011", "10101000012")
            DbListener.loadDataSet("database/person/persondata.sql")

            val personService = createPersonService(MockResponse("/graphql", generateHentIdenterBolk(*fnrList.toTypedArray())))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 3
            result.values.forEach { personId ->
                personId shouldNotBe null
            }
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal opprette nye personer fra PDL") {
            val fnrList = listOf("15467834260")
            DbListener.loadDataSet("database/person/persondata.sql")

            val personService = createPersonService(MockResponse("/graphql", generateHentIdenterBolk(*fnrList.toTypedArray())))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 1
            result[fnrList[0]] shouldNotBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal oppdatere foedselsnummer når PDL returnerer ny ident") {
            val oldFnr = "10101000098"
            val newFnr = "10101000099"
            val fnrList = listOf(oldFnr)
            DbListener.loadDataSet("database/person/persondata.sql")

            val personService =
                createPersonService(
                    MockResponse(
                        "/graphql",
                        Json.encodeToString(
                            GraphQLResponse(
                                HentIdenterBolk.Result(
                                    hentIdenterBolk =
                                        listOf(
                                            HentIdenterBolkResult(
                                                ident = oldFnr,
                                                identer =
                                                    listOf(
                                                        IdentInformasjon(newFnr, false, IdentGruppe.FOLKEREGISTERIDENT),
                                                        IdentInformasjon(oldFnr, true, IdentGruppe.FOLKEREGISTERIDENT),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                    ),
                )
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result[oldFnr] shouldNotBe null

            DbListener.dataSource.transaction { tx ->
                AuditRepository.getAuditByPersonId(tx, result[oldFnr]!!) shouldNotBeNull {
                    size shouldBe 2
                    this.any { it.tag == AuditTag.OPPRETTET_PERSON } shouldBe true
                    this.any { it.tag == AuditTag.OPPDATERT_PERSONIDENTIFIKATOR } shouldBe true
                }
            }
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal håndtere mix av eksisterende og nye personer") {
            val existingFnr = "10101000010"
            val newFnr = "15467834260"
            val fnrList = listOf(existingFnr, newFnr)
            DbListener.loadDataSet("database/person/persondata.sql")

            val personService = createPersonService(MockResponse("/graphql", generateHentIdenterBolk(newFnr)))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 2
            result[existingFnr] shouldNotBe null
            result[newFnr] shouldNotBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal returnere null for fnr som ikke fins i PDL") {
            val invalidFnr = "00000000000"
            val fnrList = listOf(invalidFnr)
            DbListener.loadDataSet("database/person/persondata.sql")

            val personService =
                createPersonService(
                    MockResponse(
                        "/graphql",
                        Json.encodeToString(
                            GraphQLResponse(
                                HentIdenterBolk.Result(
                                    hentIdenterBolk = listOf(),
                                ),
                            ),
                        ),
                    ),
                )
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)
            result[invalidFnr] shouldBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal håndtere store mengder fnr med chunking") {
            val fnrList = (1..100).map { "1010100%04d".format(it) }

            val (pdlEngine, pdlClientService) = mockPdlClientService(MockResponse("/graphql", generateHentIdenterBolk(*fnrList.toTypedArray())))
            val personService = PersonService(DbListener.dataSource, pdlClientService)

            DbListener.loadDataSet("database/person/persondata.sql")

            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM, 30)

            pdlEngine.requestHistory.size shouldBe 4
            result.size shouldBe fnrList.size
            result.values.shouldNotContainNull()
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal registrere gammelt fnr på eksisterende person id når ny ident allerede er registrert") {
            val oldFnr = "10100000098" // Ikke i DB, vil slå opp i PDL
            val existingActiveIdent = "10101000010" // Ligger allerede i DB via persondata.sql
            val fnrList = listOf(oldFnr)

            DbListener.loadDataSet("database/person/persondata.sql")

            // Mock PDL-responsen til å si at oldFnr i dag peker til existingActiveIdent som ikke er historisk
            val personService =
                createPersonService(
                    MockResponse(
                        "/graphql",
                        Json.encodeToString(
                            GraphQLResponse(
                                HentIdenterBolk.Result(
                                    hentIdenterBolk =
                                        listOf(
                                            HentIdenterBolkResult(
                                                ident = oldFnr,
                                                identer =
                                                    listOf(
                                                        IdentInformasjon(existingActiveIdent, false, IdentGruppe.FOLKEREGISTERIDENT),
                                                        IdentInformasjon(oldFnr, true, IdentGruppe.FOLKEREGISTERIDENT),
                                                    ),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                    ),
                )

            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result[oldFnr] shouldNotBe null

            // Hent audits for å sjekke at logikken for "allerede registrert" ble kjørt
            DbListener.dataSource.transaction { tx ->
                val auditLogs = AuditRepository.getAuditByPersonId(tx, result[oldFnr]!!)

                auditLogs.shouldNotBeNull {
                    // Sjekker at den nye grenen opprettet riktig OPPDATERT_PERSONIDENTIFIKATOR-audit
                    this.any {
                        it.tag == AuditTag.OPPDATERT_PERSONIDENTIFIKATOR &&
                            it.informasjon == "Oppdatert gamle foedselsnummer: $oldFnr"
                    } shouldBe true
                }
            }
        }
    })
