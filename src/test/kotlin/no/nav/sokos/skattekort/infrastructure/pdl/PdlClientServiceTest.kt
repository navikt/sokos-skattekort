package no.nav.sokos.skattekort.infrastructure.pdl

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.http.HttpStatusCode

import no.nav.pdl.enums.IdentGruppe
import no.nav.sokos.skattekort.utils.MockResponse
import no.nav.sokos.skattekort.utils.TestUtils.readFile
import no.nav.sokos.skattekort.utils.mockPdlClientService

internal class PdlClientServiceTest :
    FunSpec({

        fun createPdlClientService(
            responseBody: String,
            statusCode: HttpStatusCode = HttpStatusCode.OK,
        ): PdlClientService {
            val (_, pdlClientService) = mockPdlClientService(MockResponse("/graphql", responseBody, statusCode))
            return pdlClientService
        }

        test("hent identer fra PDL gir respons med identer") {
            val pdlClientService = createPdlClientService(readFile("/pdl/hentIdenterBolkOkResponse.json"))

            val response = pdlClientService.getIdenterBolk(listOf("12345678912", "01111953488", "40074203226"))

            response.size shouldBe 3

            response["24519539620"]?.size shouldBe 2
            response["24519539620"]?.get(0)?.historisk shouldBe true
            response["24519539620"]?.get(1)?.historisk shouldBe false
            response["24519539620"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT

            response["01111953488"]?.size shouldBe 1
            response["01111953488"]?.get(0)?.historisk shouldBe false
            response["01111953488"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT

            response["40074203226"]?.size shouldBe 1
            response["40074203226"]?.get(0)?.historisk shouldBe false
            response["40074203226"]?.get(0)?.gruppe shouldBe IdentGruppe.FOLKEREGISTERIDENT
        }

        test("hent identer fra PDL med tom array request gir PdlException") {
            val pdlClientService = createPdlClientService(readFile("/pdl/hentIdenterBolkFeilResponse.json"))

            val exception =
                shouldThrow<PdlException> {
                    pdlClientService.getIdenterBolk(emptyList())
                }

            exception.message shouldBe "Message: Ingen identer angitt."
        }

        test("hent identer fra PDL uten accesstoken returnerer at clienten ikke er autentisert") {
            val pdlClientService = createPdlClientService(readFile("/pdl/ikkeAutentisertResponse.json"))

            val exception =
                shouldThrow<PdlException> {
                    pdlClientService.getIdenterBolk(listOf("12345678912"))
                }

            exception.message shouldBe "Message: Ikke autentisert"
        }
    })
