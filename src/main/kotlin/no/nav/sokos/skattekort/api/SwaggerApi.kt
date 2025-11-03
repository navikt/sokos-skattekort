package no.nav.sokos.skattekort.api

import io.ktor.server.plugins.swagger.swaggerUI
import io.ktor.server.routing.Routing

fun Routing.swaggerApi() {
    swaggerUI(
        path = "api/v1/skattekort/docs",
        swaggerFile = "openapi/skattekort-v1-swagger.yaml",
    )
    swaggerUI(
        path = "api/v1/hent-skattekort/docs",
        swaggerFile = "openapi/sokos-skattekort-person-v1-swagger.yaml",
    )
}
