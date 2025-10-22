package no.nav.sokos.skattekort.api

import io.kotest.core.spec.style.FunSpec
import io.mockk.mockk

import no.nav.sokos.skattekort.TestUtil.withApiTestApplication
import no.nav.sokos.skattekort.module.forespoersel.ForespoerselService

class FoerespoerselApiTest :
    FunSpec({
        val forespoerselService = mockk<ForespoerselService>()

        test("bestill") {
            withApiTestApplication(api = {
                skattekortApi(forespoerselService)
            }) {
//                val response =
//                    RestAssured
//                        .given()
//                        .filter(validationFilter)
//                        .header(HttpHeaders.ContentType, APPLICATION_JSON)
//                        .header(HttpHeaders.Authorization, "Bearer $tokenWithNavIdent")
//                        .port(PORT)
//                        .get("$FASTEDATA_BASE_API_PATH/fagomraader")
//                        .then()
//                        .assertThat()
//                        .statusCode(HttpStatusCode.OK.value)
//                        .extract()
//                        .response()
            }
        }
    })
