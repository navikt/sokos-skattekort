package no.nav.sokos.skattekort.infrastructure.pdl

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import no.nav.pdl.hentpersonbolk.Navn
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.utils.createTestHttpClient
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class PdlServiceTest :
    BehaviorSpec({
        extensions(listOf(WiremockListener))

        val messageSlot = slot<AuditLogg>()
        val auditLogger =
            mockk<AuditLogger>(relaxed = true) {
                every { auditLog(capture(messageSlot)) } returns Unit
            }

        val pdlService: PdlService by lazy {
            PdlService(
                pdlClientService =
                    PdlClientService(
                        httpClient = createTestHttpClient(),
                        pdlUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
                tilgangsmaskinClientService =
                    TilgangsmaskinClientService(
                        httpClient = createTestHttpClient(),
                        tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
                auditLogger = auditLogger,
            )
        }

        val ident = "12345678910"
        val saksbehandler = mockk<Saksbehandler> { every { this@mockk.ident } returns "Z123456" }

        beforeTest {
            clearMocks(auditLogger, answers = false, recordedCalls = true, childMocks = false)
            messageSlot.clear()
        }

        Given("en saksbehandler uten tilgang til personen") {
            val problemDetailResponse =
                generateProblemDetailResponse(saksbehandler.ident, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                    )

            When("navn hentes fra PDL") {
                Then("returneres feilresponsen fra tilgangsmaskinen uten PDL-kall") {
                    WiremockListener.wiremockTilgangsmaskinStub(response = Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden)

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(0, postRequestedFor(urlEqualTo("/graphql")))

                    result.left shouldBe problemDetailResponse
                }
            }
        }

        Given("en saksbehandler med tilgang til personen") {
            When("PDL returnerer navn uten mellomnavn") {
                Then("returneres formatert navn uten mellomnavn") {
                    WiremockListener.wiremockTilgangsmaskinStub()
                    WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

                    result.get() shouldBe "Ola Nordmann"
                }
            }

            When("PDL returnerer navn med mellomnavn") {
                Then("returneres formatert navn med mellomnavn") {
                    WiremockListener.wiremockTilgangsmaskinStub()
                    WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", "mellom", "Nordmann"))))))

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

                    result.get() shouldBe "Ola mellom Nordmann"
                }
            }

            When("PDL returnerer person uten navneoppføringer") {
                Then("returneres tom streng") {
                    WiremockListener.wiremockTilgangsmaskinStub()
                    WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, null)))

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

                    result.get() shouldBe ""
                }
            }

            When("PDL-responsen ikke inneholder ident-nøkkelen") {
                Then("returneres tom streng") {
                    WiremockListener.wiremockTilgangsmaskinStub()
                    WiremockListener.wiremockPDLStub("{}")

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

                    result.get() shouldBe ""
                }
            }

            When("oppslaget lykkes") {
                Then("returneres navnet fra PDL og audit-logg skrives") {
                    WiremockListener.wiremockTilgangsmaskinStub()
                    WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))

                    val result = pdlService.getPersonNavn(ident, saksbehandler)
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
                    WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

                    result.get() shouldBe "Ola Nordmann"
                    verify(exactly = 1) { auditLogger.auditLog(any()) }

                    messageSlot.captured shouldNotBeNull {
                        saksbehandler shouldBe saksbehandler
                        fnr shouldBe ident
                        brukerhandling shouldBe "NAV-ansatt har gjort et oppslag på bruker for å hente navn"
                    }
                }
            }
        }
    })
