package no.nav.sokos.skattekort.infrastructure.tilgangsmaskin

import kotlinx.serialization.json.Json

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.sokos.skattekort.utils.MockResponse
import no.nav.sokos.skattekort.utils.PathMatchType
import no.nav.sokos.skattekort.utils.generateProblemDetailResponse
import no.nav.sokos.skattekort.utils.mockTilgangsmaskinClientService
import no.nav.tilgangsmaskinen.ProblemDetailResponse

class TilgangsmaskinClientServiceTest :
    FunSpec({
        val ansattId = "Z123456"

        fun createService(
            responseBody: String = "",
            statusCode: HttpStatusCode = HttpStatusCode.NoContent,
        ): TilgangsmaskinClientService {
            val (_, tilgangsmaskinClientService) = mockTilgangsmaskinClientService(MockResponse("/api/v1/ccf/kjerne/", responseBody, statusCode, PathMatchType.PREFIX))
            return tilgangsmaskinClientService
        }

        test("should return statusCode 204 when saksbehandler has access") {
            val tilgangsmaskinClientService = createService()

            val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
            response shouldBe null
        }

        test("should return statusCode 403 when AVVIST_FORTROLIG_ADRESSE") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med fortrolig adresse",
                    )
            val tilgangsmaskinClientService = createService(Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden)

            val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
            response shouldBe problemDetailResponse
        }

        test("should return statusCode 403 when AVVIST_STRENGT_FORTROLIG_ADRESSE") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_STRENGT_FORTROLIG_ADRESSE,
                        begrunnelse = "Du har ikke tilgang til brukere med strengt fortrolig adresse",
                    )
            val tilgangsmaskinClientService = createService(Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden)

            val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
            response shouldBe problemDetailResponse
        }

        test("should return statusCode 403 when AVVIST_SKJERMING") {
            val problemDetailResponse =
                generateProblemDetailResponse(ansattId, "12345678910")
                    .copy(
                        title = ProblemDetailResponse.Title.AVVIST_SKJERMING,
                        begrunnelse = "Du har ikke tilgang til Nav-ansatte og andre skjermede brukere",
                    )
            val tilgangsmaskinClientService = createService(Json.encodeToString(problemDetailResponse), HttpStatusCode.Forbidden)

            val response = tilgangsmaskinClientService.checkSaksbehandlerAccess(ansattId, "12345678910")
            response shouldBe problemDetailResponse
        }
    })
