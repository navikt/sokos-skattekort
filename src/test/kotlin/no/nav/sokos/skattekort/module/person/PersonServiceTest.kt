package no.nav.sokos.skattekort.person

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotContainNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

import no.nav.pdl.HentIdenterBolk
import no.nav.pdl.enums.IdentGruppe
import no.nav.pdl.hentidenterbolk.HentIdenterBolkResult
import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.pdl.GraphQLResponse
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.listener.DbListener
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generatePDLResponse
import no.nav.sokos.skattekort.util.SQLUtils.transaction

class PersonServiceTest :
    FunSpec({
        extensions(DbListener, WiremockListener)

        val pdlClientService: PdlClientService by lazy {
            PdlClientService(
                httpClient = createHttpClient(),
                pdlUrl = WiremockListener.wiremock.baseUrl(),
                azuredTokenClient = WiremockListener.azuredTokenClient,
            )
        }

        val personService by lazy {
            PersonService(DbListener.dataSource, pdlClientService)
        }

        test("Skal ikke kaste exception når det kommer inn en tom liste") {
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(emptyList(), AUDIT_SYSTEM)
            result.size shouldBe 0
        }

        test("findOrCreatePersonByFnr skal returnere en person som er registrert") {
            val fnr = "10101000010"
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

            WiremockListener.wiremockPDLStub(generatePDLResponse(*fnrList.toTypedArray()))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 3
            result.values.forEach { personId ->
                personId shouldNotBe null
            }
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal opprette nye personer fra PDL") {
            val fnrList = listOf("15467834260")
            DbListener.loadDataSet("database/person/persondata.sql")

            WiremockListener.wiremockPDLStub(generatePDLResponse(*fnrList.toTypedArray()))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 1
            result[fnrList[0]] shouldNotBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal oppdatere foedselsnummer når PDL returnerer ny ident") {
            val oldFnr = "10101000098"
            val newFnr = "10101000099"
            val fnrList = listOf(oldFnr)
            DbListener.loadDataSet("database/person/persondata.sql")

            WiremockListener.wiremockPDLStub(
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

            WiremockListener.wiremockPDLStub(generatePDLResponse(newFnr))
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)

            result.size shouldBe 2
            result[existingFnr] shouldNotBe null
            result[newFnr] shouldNotBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal returnere null for fnr som ikke fins i PDL") {
            val invalidFnr = "00000000000"
            val fnrList = listOf(invalidFnr)
            DbListener.loadDataSet("database/person/persondata.sql")

            WiremockListener.wiremockPDLStub(
                Json.encodeToString(
                    GraphQLResponse(
                        HentIdenterBolk.Result(
                            hentIdenterBolk =
                                listOf(),
                        ),
                    ),
                ),
            )
            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM)
            result[invalidFnr] shouldBe null
        }

        test("getPersonIdAndCheckFoedselsnumreIsUpdated skal håndtere store mengder fnr med chunking") {
            val fnrList = (1..100).map { "1010100%04d".format(it) }

            WiremockListener.wiremockPDLStub(generatePDLResponse(*fnrList.toTypedArray()))
            DbListener.loadDataSet("database/person/persondata.sql")

            val result = personService.getPersonIdAndCheckFoedselsnumreIsUpdated(fnrList, AUDIT_SYSTEM, 30)

            WiremockListener.wiremock.verify(4, postRequestedFor(urlEqualTo("/graphql")))
            result.size shouldBe fnrList.size
            result.values.shouldNotContainNull()
        }
    })
