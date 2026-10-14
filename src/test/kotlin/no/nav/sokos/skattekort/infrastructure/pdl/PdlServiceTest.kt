package no.nav.sokos.skattekort.infrastructure.pdl

import kotlinx.serialization.json.Json

import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkObject
import io.mockk.verify

import no.nav.pdl.hentpersonbolk.Navn
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.config.createHttpClient
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.listener.WiremockListener
import no.nav.sokos.skattekort.listener.WiremockListener.generateProblemDetailResponse
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class PdlServiceTest :
    FunSpec({
        extensions(listOf(WiremockListener))

        val messageSlot = slot<AuditLogg>()

        val pdlService: PdlService by lazy {
            PdlService(
                pdlClientService =
                    PdlClientService(
                        httpClient = createHttpClient(),
                        pdlUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
                tilgangsmaskinClientService =
                    TilgangsmaskinClientService(
                        httpClient = createHttpClient(),
                        tilgangsmaskinUrl = WiremockListener.wiremock.baseUrl(),
                        azuredTokenClient = WiremockListener.azuredTokenClient,
                    ),
            )
        }

        val ident = "12345678910"
        val saksbehandler = mockk<Saksbehandler> { every { this@mockk.ident } returns "Z123456" }

        beforeTest {
            mockkObject(AuditLogger)
            every { AuditLogger.auditLog(capture(messageSlot)) } just runs
            messageSlot.clear()
        }

        afterTest {
            unmockkObject(AuditLogger)
        }

        test("returns left when access is denied and does not call PDL") {
            val problemDetailResponse =
                generateProblemDetailResponse(saksbehandler.ident, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                    )

            WiremockListener.wiremockTilgangsmaskinStub(response = Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden)

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(0, postRequestedFor(urlEqualTo("/graphql")))

            result.left shouldBe problemDetailResponse
        }

        test("returns right with formatted name when middle name is null") {
            WiremockListener.wiremockTilgangsmaskinStub()
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
            WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

            result.get() shouldBe "Ola Nordmann"
        }

        test("returns right with formatted name when middle name is present") {
            WiremockListener.wiremockTilgangsmaskinStub()
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", "mellom", "Nordmann"))))))

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
            WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

            result.get() shouldBe "Ola mellom Nordmann"
        }

        test("returns right with empty string when PDL returns person without any name entries") {
            WiremockListener.wiremockTilgangsmaskinStub()
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, null)))

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
            WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

            result.get() shouldBe ""
        }

        test("returns right with empty string when PDL response does not contain ident key") {
            WiremockListener.wiremockTilgangsmaskinStub()
            WiremockListener.wiremockPDLStub("{}")

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
            WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

            result.get() shouldBe ""
        }

        test("always writes audit log before access check and PDL call") {
            WiremockListener.wiremockTilgangsmaskinStub()
            WiremockListener.wiremockPDLStub(WiremockListener.generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            WiremockListener.wiremock.verify(1, postRequestedFor(urlPathMatching("/api/v1/ccf/kjerne/.*")))
            WiremockListener.wiremock.verify(1, postRequestedFor(urlEqualTo("/graphql")))

            verify(exactly = 1) { AuditLogger.auditLog(any()) }

            result.get() shouldBe "Ola Nordmann"
            messageSlot.captured shouldNotBeNull {
                saksbehandler shouldBe saksbehandler
                fnr shouldBe ident
                brukerhandling shouldBe "NAV-ansatt har gjort et oppslag på bruker for å hente navn"
            }
        }
    })
