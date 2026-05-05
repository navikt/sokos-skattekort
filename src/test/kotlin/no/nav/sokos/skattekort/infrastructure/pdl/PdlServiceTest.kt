package no.nav.sokos.skattekort.infrastructure.pdl

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

import no.nav.pdl.hentpersonbolk.Navn
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.security.Saksbehandler
import no.nav.sokos.skattekort.util.audit.AuditLogg
import no.nav.sokos.skattekort.util.audit.AuditLogger
import no.nav.sokos.skattekort.utils.MockResponse
import no.nav.sokos.skattekort.utils.PathMatchType
import no.nav.sokos.skattekort.utils.generateHentPersonBolk
import no.nav.sokos.skattekort.utils.generateProblemDetailResponse
import no.nav.sokos.skattekort.utils.mockPdlClientService
import no.nav.sokos.skattekort.utils.mockTilgangsmaskinClientService
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class PdlServiceTest :
    FunSpec({

        val messageSlot = slot<AuditLogg>()
        val auditLogger =
            mockk<AuditLogger>(relaxed = true) {
                every { auditLog(capture(messageSlot)) } returns Unit
            }

        val ident = "12345678910"
        val saksbehandler = mockk<Saksbehandler> { every { this@mockk.ident } returns "Z123456" }

        fun createPdlService(
            pdlResponses: List<MockResponse> = emptyList(),
            tilgangsmaskinResponses: List<MockResponse> = emptyList(),
        ): Triple<MockEngine, MockEngine, PdlService> {
            val (pdlEngine, pdlClientService) = mockPdlClientService(*pdlResponses.toTypedArray())
            val (tilgangsmaskinEngine, tilgangsmaskinClientService) = mockTilgangsmaskinClientService(*tilgangsmaskinResponses.toTypedArray())
            return Triple(
                pdlEngine,
                tilgangsmaskinEngine,
                PdlService(
                    pdlClientService = pdlClientService,
                    tilgangsmaskinClientService = tilgangsmaskinClientService,
                    auditLogger = auditLogger,
                ),
            )
        }

        beforeTest {
            clearMocks(auditLogger, answers = false, recordedCalls = true, childMocks = false)
            messageSlot.clear()
        }

        test("returns left when access is denied and does not call PDL") {
            val problemDetailResponse =
                generateProblemDetailResponse(saksbehandler.ident, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                    )

            val (pdlEngine, _, pdlService) =
                createPdlService(
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            pdlEngine.requestHistory.size shouldBe 0

            result.left shouldBe problemDetailResponse
        }

        test("returns right with formatted name when middle name is null") {
            val (pdlEngine, tilgangsmaskinEngine, pdlService) =
                createPdlService(
                    pdlResponses = listOf(MockResponse("/graphql", generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))),
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", "", HttpStatusCode.NoContent, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            tilgangsmaskinEngine.requestHistory.size shouldBe 1
            pdlEngine.requestHistory.size shouldBe 1

            result.get() shouldBe "Ola Nordmann"
        }

        test("returns right with formatted name when middle name is present") {
            val (pdlEngine, tilgangsmaskinEngine, pdlService) =
                createPdlService(
                    pdlResponses = listOf(MockResponse("/graphql", generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", "mellom", "Nordmann"))))))),
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", "", HttpStatusCode.NoContent, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            tilgangsmaskinEngine.requestHistory.size shouldBe 1
            pdlEngine.requestHistory.size shouldBe 1

            result.get() shouldBe "Ola mellom Nordmann"
        }

        test("returns right with empty string when PDL returns person without any name entries") {
            val (pdlEngine, tilgangsmaskinEngine, pdlService) =
                createPdlService(
                    pdlResponses = listOf(MockResponse("/graphql", generateHentPersonBolk(Pair(ident, null)))),
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", "", HttpStatusCode.NoContent, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            tilgangsmaskinEngine.requestHistory.size shouldBe 1
            pdlEngine.requestHistory.size shouldBe 1

            result.get() shouldBe ""
        }

        test("returns right with empty string when PDL response does not contain ident key") {
            val (pdlEngine, tilgangsmaskinEngine, pdlService) =
                createPdlService(
                    pdlResponses = listOf(MockResponse("/graphql", "{}")),
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", "", HttpStatusCode.NoContent, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            tilgangsmaskinEngine.requestHistory.size shouldBe 1
            pdlEngine.requestHistory.size shouldBe 1

            result.get() shouldBe ""
        }

        test("always writes audit log before access check and PDL call") {
            val (pdlEngine, tilgangsmaskinEngine, pdlService) =
                createPdlService(
                    pdlResponses = listOf(MockResponse("/graphql", generateHentPersonBolk(Pair(ident, Person(listOf(Navn("Ola", null, "Nordmann"))))))),
                    tilgangsmaskinResponses = listOf(MockResponse("/api/v1/ccf/kjerne/", "", HttpStatusCode.NoContent, PathMatchType.PREFIX)),
                )

            val result = pdlService.getPersonNavn(ident, saksbehandler)
            tilgangsmaskinEngine.requestHistory.size shouldBe 1
            pdlEngine.requestHistory.size shouldBe 1

            verify(exactly = 1) { auditLogger.auditLog(any()) }

            result.get() shouldBe "Ola Nordmann"
            messageSlot.captured shouldNotBeNull {
                saksbehandler shouldBe saksbehandler
                fnr shouldBe ident
                brukerhandling shouldBe "NAV-ansatt har gjort et oppslag på bruker for å hente navn"
            }
        }
    })
