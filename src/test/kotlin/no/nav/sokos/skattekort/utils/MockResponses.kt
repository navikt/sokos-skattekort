package no.nav.sokos.skattekort.utils

import kotlinx.serialization.json.Json

import io.ktor.client.engine.mock.MockEngine
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.mockk

import no.nav.pdl.HentIdenterBolk
import no.nav.pdl.HentPersonBolk
import no.nav.pdl.enums.IdentGruppe
import no.nav.pdl.hentidenterbolk.HentIdenterBolkResult
import no.nav.pdl.hentidenterbolk.IdentInformasjon
import no.nav.pdl.hentpersonbolk.HentPersonBolkResult
import no.nav.pdl.hentpersonbolk.Person
import no.nav.sokos.skattekort.config.PropertiesConfig
import no.nav.sokos.skattekort.infrastructure.pdl.GraphQLResponse
import no.nav.sokos.skattekort.infrastructure.pdl.PdlClientService
import no.nav.sokos.skattekort.infrastructure.tilgangsmaskin.TilgangsmaskinClientService
import no.nav.sokos.skattekort.security.AzuredTokenClient
import no.nav.tilgangsmaskinen.ProblemDetailResponse

val azuredTokenClient: AzuredTokenClient =
    mockk<AzuredTokenClient>().also {
        coEvery { it.getSystemToken() } returns "token"
    }

fun mockPdlClientService(vararg mockResponses: MockResponse): Pair<MockEngine, PdlClientService> {
    val pdlUrl = PropertiesConfig.pdlProperties.pdlUrl
    val adjustedResponses =
        mockResponses
            .map { response ->
                response.copy(path = "/$pdlUrl${response.path}")
            }.toTypedArray()
    val (engine, client) = MockHttpClient.create(*adjustedResponses)
    return engine to PdlClientService(httpClient = client, azuredTokenClient = azuredTokenClient)
}

fun mockTilgangsmaskinClientService(vararg mockResponses: MockResponse): Pair<MockEngine, TilgangsmaskinClientService> {
    val (engine, client) = MockHttpClient.create(*mockResponses)
    return engine to TilgangsmaskinClientService(httpClient = client, tilgangsmaskinUrl = "http://localhost", azuredTokenClient = azuredTokenClient)
}

fun generateHentIdenterBolk(vararg fnr: String): String =
    Json.encodeToString(
        GraphQLResponse(
            HentIdenterBolk.Result(
                hentIdenterBolk =
                    fnr.toList().map { value ->
                        HentIdenterBolkResult(
                            ident = value,
                            identer = listOf(IdentInformasjon(value, false, IdentGruppe.FOLKEREGISTERIDENT)),
                        )
                    },
            ),
        ),
    )

fun generateHentPersonBolk(vararg fnrMedNavn: Pair<String, Person?>): String =
    Json.encodeToString(
        GraphQLResponse(
            HentPersonBolk.Result(
                hentPersonBolk =
                    fnrMedNavn.toList().map { value ->
                        HentPersonBolkResult(ident = value.first, person = value.second)
                    },
            ),
        ),
    )

fun generateProblemDetailResponse(
    ansattId: String,
    fnr: String,
): ProblemDetailResponse =
    ProblemDetailResponse(
        instance = "$ansattId/$fnr",
        status = HttpStatusCode.Forbidden.value,
        title = ProblemDetailResponse.Title.AVVIST_FORTROLIG_ADRESSE,
        type = "https://confluence.adeo.no/display/TM/Tilgangsmaskin+API+og+regelsett",
        brukerIdent = fnr,
        navIdent = ansattId,
        begrunnelse = "",
        traceId = "32cf46af79a38f39ad047303499d0bbf",
        kanOverstyres = false,
    )
